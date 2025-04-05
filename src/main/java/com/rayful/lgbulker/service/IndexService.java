package com.rayful.lgbulker.service;

import com.rayful.lgbulker.vo.BulkerLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static com.rayful.lgbulker.util.BulkerUtils.printBulkerLog;

@RequiredArgsConstructor
@Slf4j
@Service
public class IndexService {

  @Value("${elasticsearch.bulk_idx_url}")
  private String INDEXING_URL;    //마. 검색인덱스URL
  @Value("${elasticsearch.idx_refresh}")
  private String REFRESH_URL;      //바. 색인요청후 refresh
  @Value("${elasticsearch.auth}")
  private String ES_AUTH;                 //사. ES 접속 계정(id/pw 인증 필요시 사용)
  @Value("${app.paths.output.bulkfiles}")
  private String BULK_PATH;       //다. ES에 색인위한 bulk파일 저장경로

  //로그 수집위해 사용.
  BulkerLogVO bulkerLogVO = new BulkerLogVO();

  int Total_EmCount;    // 총 처리대상 이메일 카운트
  int Total_AttachCount;    // 총 처리대상 이메일 카운트

  List<String> totalAttachIds = new ArrayList<>(); //총 처리대상 첨부파일 id

  private final RestTemplate restTemplate;
  public void doIndexing() throws IOException {

    File[] files = new File(BULK_PATH).listFiles((dir, name) -> name.startsWith("bulk") && name.endsWith(".json"));
    if (files == null || files.length == 0) {
      log.info("색인할 파일이 없습니다.");
      return;
    }
    Arrays.sort(files);

    String authHeader = "Basic " + Base64.getEncoder().encodeToString(ES_AUTH.getBytes());
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", authHeader);


    for (File file : files) {
      indexFileToElastic(file, headers);
    }
    log.info("색인 작업 완료");

    //총 처리 json 파일건수
    bulkerLogVO.index.setTotalCount(files.length);


// 작업 요약정보출력
    printBulkerLog(bulkerLogVO);

  }

  private void indexFileToElastic(File file, HttpHeaders headers) throws IOException {
    boolean isSuccess = false;


    log.info("색인 중: {}", file.getName());
    String content = Files.readString(file.toPath());
    HttpEntity<String> request = new HttpEntity<>(content, headers);

    ResponseEntity<Map<String, Object>> responseEntity = (ResponseEntity) this.restTemplate.exchange(INDEXING_URL, HttpMethod.POST, request, Map.class);
//    log.info("[INDEX] responseEntity: {}", responseEntity);
    Map<String, Object> responseBody = responseEntity.getBody();

    if(responseEntity.getStatusCodeValue() == org.apache.http.HttpStatus.SC_OK) {
      if(responseBody.containsKey("errors")) {
        isSuccess = !((Boolean) responseBody.get("errors"));
      }

      // 개별 결과를  확인하여 색인결과 카운트에 반영한다.
      if(responseBody.containsKey("items")) {
        List<Map<String, Object>> itemList = (List<Map<String, Object>>)responseBody.get("items");
        Map<String, Object> itemData = null;
        Map<String, Object> errorMap = null;
        String result = null;
        for(Map<String, Object> itemMap : itemList) {
          if(itemMap.containsKey("index")) {
            itemData = (Map<String, Object>) itemMap.get("index");
          } else if(itemMap.containsKey("update")) {
            itemData = (Map<String, Object>) itemMap.get("update");
          } else if(itemMap.containsKey("delete")) {
            itemData = (Map<String, Object>) itemMap.get("delete");
          } else {
            itemData = null;
            log.warn("Can't find item's key : index or delete or update");
          }

          if(itemData != null) {
            if(itemData.containsKey("result")) {
              result = (String)itemData.get("result");

              if("created".equals(result)) {
                bulkerLogVO.indexResult.increaseCreateCount();
              } else if("updated".equals(result)) {
                bulkerLogVO.indexResult.increaseUpdateCount();
              } else if("deleted".equals(result)) {
                bulkerLogVO.indexResult.increaseDeleteCount();
              } else if("noop".equals(result)) {
                bulkerLogVO.indexResult.increasePartialCount();
              } else {
                bulkerLogVO.indexResult.increaseEtcCount(result);
              }

              //성곤건수증가
              bulkerLogVO.index.increaseSuccessCount();

            } else if(itemData.containsKey("error")) {
              errorMap = (Map<String, Object>)itemData.get("error");
              bulkerLogVO.indexResult.increaseErrorCount((String) errorMap.get("reason"));

            } else {
              log.warn("Can't find itemData's key : result or error");
            }
          }
        }
      }
    }

    HttpEntity<Void> refreshRequest = new HttpEntity<>(headers);
    ResponseEntity<String> refreshResponse = restTemplate.exchange(REFRESH_URL, HttpMethod.POST, refreshRequest, String.class);

    if (!refreshResponse.getStatusCode().is2xxSuccessful()) {
      log.warn("색인 refresh 실패: {}", refreshResponse.getStatusCode());
    }
  }


}
