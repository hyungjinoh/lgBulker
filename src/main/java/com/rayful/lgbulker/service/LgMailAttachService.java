package com.rayful.lgbulker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rayful.lgbulker.mapper.EmailMapper;
import com.rayful.lgbulker.vo.BulkerLogVO;
import com.rayful.lgbulker.util.LGAttachFileWriter;
import com.rayful.lgbulker.vo.FileVO;
import com.rayful.lgbulker.vo.LGAttachVO;
import com.rayful.lgbulker.vo.LGEmailVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LgMailAttachService {

  // --- 설정 값 주입 ---
  @Value("${app.batch.size}")
  private int BATCH_SIZE;
  @Value("${app.paths.input.emails}")
  private String EMAILS_DIR;          //가. 처리대상 json 형식 이메일 파일들 경로
  @Value("${app.paths.input.attaches}")
  private String ATTACHES_DIR;     //나. 처리대상 : 가. 를 첨부기준 json 형식으로 저장한 결과

  @Value("${app.paths.output.merged}")
  private String MERGED_DIR;     //다. 첨부+이메일 통합 파일 저장경로
  @Value("${app.paths.output.bulkfiles}")
  private String BULK_PATH;       //다. ES에 색인위한 bulk파일 저장경로
  @Value("${app.paths.attachments}")
  private String ATTACHMENTS_PATH;     //라. 첨부파일저장경로1
  @Value("${elasticsearch.bulk_idx_url}")
  private String INDEXING_URL;    //마. 검색인덱스URL
  @Value("${elasticsearch.idx_refresh}")
  private String REFRESH_URL;      //바. 색인요청후 refresh
  @Value("${elasticsearch.auth}")
  private String ES_AUTH;                 //사. ES 접속 계정(id/pw 인증 필요시 사용)

  private final EmailMapper emailMapper;
  //외부필터 사용.
//  private final ExternalConverter externalConverter;

  //로그 수집위해 사용.
  BulkerLogVO bulkerLogVO = new BulkerLogVO();

  int Total_EmCount;    // 총 처리대상 이메일 카운트
  int Total_AttachCount;    // 총 처리대상 이메일 카운트

  List<String> totalAttachIds = new ArrayList<>(); //총 처리대상 첨부파일 id

  private RestTemplate restTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();
//  private final String mailJsonPath = "src/main/resources/data/20250404_08_25_mail_0.json";
//  private final String attachDirPath = "src/main/resources/data/attach";

  private final List<LGAttachVO> result = new ArrayList<>();


    public void load() {
    try {
      // 1. 메일 로드
      // ✅ 1. 메일 JSON 여러 개 로딩
      List<LGEmailVo> mails = new ArrayList<>();
      File mailDir = new File(EMAILS_DIR); // 메일 저장경로
      File[] mailFiles = mailDir.listFiles((dir, name) -> name.endsWith(".json"));

      if (mailFiles != null) {
        for (File file : mailFiles) {
          List<LGEmailVo> mailListInFile = objectMapper.readValue(
                  file,
                  new TypeReference<List<LGEmailVo>>() {}
          );
          mails.addAll(mailListInFile);
        }
      }

      Map<String, LGEmailVo> mailMap = mails.stream().collect(Collectors.toMap(LGEmailVo::getMailGUID, mail -> mail));

      Set<String> matchedMailGuids = new HashSet<>();

      // 2. 첨부파일 로드 (파일당 List<AttachFileVo>)
      File attachDir = new File(ATTACHES_DIR);  //첨부파일 저장경로
      File[] files = attachDir.listFiles((dir, name) -> name.endsWith(".json"));

      List<FileVO> fileVOList = new ArrayList<>();

      if (files != null) {
        for (File file : files) {
          fileVOList = objectMapper.readValue(file, new TypeReference<List<FileVO>>() {
          });

          //첨부기준으로 실행.
          for (FileVO attach : fileVOList) {
            String mailGuid = attach.getMailGUID();
            LGEmailVo matchedMail = mailMap.get(mailGuid);
            matchedMailGuids.add(mailGuid);

            String key = attach.getFileGUID() + "_" + mailGuid;

            LGAttachVO vo = LGAttachVO.builder().key(key).attachFile(attach).email(matchedMail).build();

            vo.fillDerivedFields(); // ✅ 파생 필드 세팅
            result.add(vo);
          }
        }
      }

      //이메일기준 실행, Mail_GUID 값이  AttachFileVo 에 없으면 LGAttachVO 생성후 결과리스트에 추가
      // ✅ 4. 첨부파일이 없는 메일도 result 에 추가
      for (LGEmailVo mail : mails) {
        String mailGuid = mail.getMailGUID();
        if (!matchedMailGuids.contains(mailGuid)) {
          String key = "unknown_" + mailGuid;

          LGAttachVO vo = LGAttachVO.builder().key(key).attachFile(null).email(mail).build();

          vo.fillDerivedFields(); // ✅ 파생 필드 세팅
          result.add(vo);
        }
      }

      // 4. 출력
      /*log.info("=== LGAttachVO 출력 ===");
      for (LGAttachVO vo : result) {
        log.info("Key: {}", vo.getKey());
        log.info(" - 첨부파일: {}", vo.getAttachFile() != null ? vo.getAttachFile().getFileName() : "없음");
        log.info(" - 메일 제목: {}", vo.getEmail() != null ? vo.getEmail().getMailSubject() : "없음");
        log.info(" - LGAttachVO : {}", vo.toPrettyJson());
      }*/

      // 통합형식의 json 파일 저장 : bulk파일로 변환전.
      LGAttachFileWriter.writeAsJsonFiles(result, MERGED_DIR);

    } catch (Exception e) {
      log.error("로딩 실패", e);
    }


  }

  // createBulkFiles(), doIndexing() 등은 동일 방식으로 별도 메서드로 유지 가능

  public void createBulkFiles() throws IOException {
    File[] files = new File(MERGED_DIR).listFiles((dir, name) -> name.contains("attach_"));
    if (files == null || files.length == 0) {
      log.warn("처리할 JSON 파일이 없습니다.");
      return;
    }

    Files.createDirectories(Path.of(BULK_PATH));

    for (File file : files) {
      createBulkFileFromAttachJson(file);
    }

    /////////////////////////////////////
    //    로그세팅
    /////////////////////////////////////
    Total_AttachCount = totalAttachIds.size();
    bulkerLogVO.attach.setTotalCount(Total_AttachCount);
//    log.info("=============처리대상 총 첨부파일 갯수 : {}", bulkerLogVO.attach.getTotalCount());
  }


  private void createBulkFileFromAttachJson(File file) {
    try {
      List<LGAttachVO> attachList = objectMapper.readValue(file, new TypeReference<>() {
      });
      File bulkFile = new File(BULK_PATH, "bulk_" + file.getName());

      try (BufferedWriter writer = new BufferedWriter(new FileWriter(bulkFile))) {
        for (LGAttachVO attach : attachList) {
          String _id = attach.getAttach_id();
          String meta = String.format("{\"index\": {\"_index\": \"idx_email\", \"_id\": \"%s\"}}", (_id.contains("_attach_") ? _id : attach.getEm_id()));
          writer.write(meta);
          writer.newLine();
          writer.write(objectMapper.writeValueAsString(objectMapper.valueToTree(attach)));
          writer.newLine();
        }
      }
      log.info("{} >> {} 변환 완료", file.getName(), bulkFile.getName());

      ///////////////////
      long attachCount = attachList.stream()
                                   .map(LGAttachVO::getAttach_id)     // attach_id 추출
                                   .filter(Objects::nonNull)        // null 값 방지
                                   .distinct()                      // 고유값으로 필터링
                                   .count();                        // 개수 세기

//      log.info("Uniq attach_id 개수: {}", attachCount);
      Set<String> uniqueAttachIds = attachList.stream()
                                              .map(LGAttachVO::getAttach_id)
                                              .filter(Objects::nonNull)
                                              .collect(Collectors.toSet());

//      log.info("Uniq attach_id 목록: {}", uniqueAttachIds);
      List<String> allList = uniqueAttachIds.stream()
                                            .filter(id -> id != null && !id.isBlank())  // null 또는 공백 문자열 제외
                                            .collect(Collectors.toList());

      totalAttachIds.addAll(allList);         // 고유 attach_id를 누적시킴 :통계

      ////////////////////////
    } catch (IOException e) {
      log.error("bulk 파일 처리 중 오류 발생: {}", file.getName(), e);
    }
  }

}
