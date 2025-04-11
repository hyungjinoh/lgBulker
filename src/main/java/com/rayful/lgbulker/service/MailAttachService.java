package com.rayful.lgbulker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rayful.lgbulker.util.ContentTypeDetector;
import com.rayful.lgbulker.util.ExternalConverter;
import com.rayful.lgbulker.util.FileUtils;
import com.rayful.lgbulker.vo.*;
import com.rayful.lgbulker.util.BulkFileWriter;
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

import static org.apache.logging.log4j.core.util.FileUtils.getFileExtension;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailAttachService {

  // --- 설정 값 주입 ---
  @Value("${app.paths.input.emails}")
  private String EMAILS_JSON_DIR;          //가. 처리대상 json 형식 이메일 파일들 경로
  @Value("${app.paths.input.files}")
  private String FILES_JSON_DIR;     //나. 처리대상 : 가. 를 첨부기준 json 형식으로 저장한 결과

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

  private final FileService fileService;  //첨부파일 압축해제 unzip
  private final ExternalConverter externalConverter;    //외부필터 사용.
  private final BulkFileWriter bulkFileWriter;
  private final BodyParserService bodyParserService;
  private final FileDownloadService fileDownloadService;
  //ocr
  private final EmailAttachmentProcessService emailAttachmentProcessService;

  //로그 수집위해 사용.
  BulkerLogVO bulkerLogVO = new BulkerLogVO();

  int Total_EmCount;    // 총 처리대상 이메일 카운트
  int Total_AttachCount;    // 총 처리대상 이메일 카운트

  List<String> totalAttachIds = new ArrayList<>(); //총 처리대상 첨부파일 id

  private RestTemplate restTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();
//  private final String mailJsonPath = "src/main/resources/data/20250404_08_25_mail_0.json";
//  private final String attachDirPath = "src/main/resources/data/attach";

  public void load() {
    try {

      ///데이터 로드시작
      List<LGEmailVo> emails = loadEmails();  //이메일 json 파일 읽음
      List<LGFileVO> files = loadFiles();     //첨부 json 파일 읽음

      Map<String, LGEmailVo> mailMap = mapEmailsByGuid(emails); // 고유한 MailGUID만 뽑아 별도보관 (모든 이메일정보 포함)

      /***
       *  // 결과타입A : 파일-메일 매치된것만 포함
       *  // 결과타입B : 파일x 메일만 존재한것만 포함
       *  // 결과타입C  : 결과타입A에서 파일확장자가 .zip 인 파일만 뽑아 별도저장
       *  // 결과타입D :  ResultA - ResultC : 파일메일에서 첨부제외, 첨부파일은 별도 처리
       */
      List<LGFileMailVO> resultListA = processAttachments(mailMap);
      List<LGFileMailVO> resultListB = processEmailsWithoutAttachments(resultListA, emails);
      List<LGFileMailVO> resultListC = getList_With_Zipfile(resultListA);
      List<LGFileMailVO> resultListD = resultListA.stream()
                                                   .filter(vo -> resultListC.stream().noneMatch(c -> c.getKey().equals(vo.getKey())))
                                                   .collect(Collectors.toList());

      //첨부파일만 있는것 압축풀고, 일반 파일들로 구성된것들  일반파일 목록에 추가
      List<LGFileMailVO> unzippedList = fileService.checkFile_Unzip_if_Zipfile(resultListC);

      //---------------------중간결과파일, 링크처리전---------------------------
      List<LGFileMailVO> resultListAll = new ArrayList<>();
      resultListAll.addAll(resultListB);  //이메일만
      resultListAll.addAll(resultListD);   //일반파일만
      resultListAll.addAll(unzippedList);    //첨부파일만 있는것 압축풀고, 푼것들 일반파일 목록에 추가

      /***
       * 링크 처리부분 시작
       * 입력받은 리스트에서, 본문링크 있는것과 없는것들을 각각 구분해서 분리저장함.
       * 링크 분리해 링크 없에고 일반파일 목록으로 구분 : linkRemovedList
       * linkRemovedList 에서 첨부파일 있는놈들만 별도로 추출 : linkRemovedList_With_Zip
       * 첨부파일만 있는것 압축풀고, 푼것들 일반파일 목록에 추가 : resultListD_Link_With_Zip_unzipped
       */
      List<LGFileMailVO> yesBodyLinkList = new ArrayList<>();   //링크 있는것
      List<LGFileMailVO> noBodyLinkList = new ArrayList<>();    //링크 없는것
      processBodyLinks(resultListAll, yesBodyLinkList, noBodyLinkList);
      List<LGFileMailVO> linkRemovedList = splitByBodyLinks(yesBodyLinkList);;
      List<LGFileMailVO> linkRemovedList_With_Zip = getList_With_Zipfile(linkRemovedList);
      List<LGFileMailVO> linkRemovedList_With_Zip_unzipped = fileService.checkFile_Unzip_if_Zipfile(linkRemovedList_With_Zip);

      /***
       * 최종결과 리스트 : 링크없는목록+링크있는목록+링크제거된목록(일반)+링크제거된목록(첨부파일에서압축푼것)
       */
      List<LGFileMailVO> finalResultList = new ArrayList<>();
      finalResultList.addAll(noBodyLinkList);
      finalResultList.addAll(yesBodyLinkList);
      finalResultList.addAll(linkRemovedList);
      finalResultList.addAll(linkRemovedList_With_Zip_unzipped);

      /***
       * 리스트를 맵으로 변환, 서차장이 파일 첨부파일처리, OCR 처리 하는 부분
       */
      List<Map<String, Object>> convertedMapList = convertToMapList(finalResultList);


      //사이냅 필터처리, 이미지 처리 등등.... 부분  -- 오형진
//      enrichAttachBodies(convertedMapList);

      List<Map<String, Object>> finalMapList = emailAttachmentProcessService.processEmailAttachments(convertedMapList);


      //벌크파일 생성호출
      bulkFileWriter.writeAsBulkJsonFiles(finalMapList, MERGED_DIR);

    } catch (Exception e) {
      log.error("로딩 실패", e);
    }
  }

  private static List<LGFileMailVO> getList_With_Zipfile(List<LGFileMailVO> resultListA) {
    List<LGFileMailVO> resultListC = new ArrayList<>();
    for(LGFileMailVO itemVo : resultListA) {
        String attachFile = itemVo.getAttach_path();

        if(!attachFile.isEmpty()) {
          File file = new File(attachFile);
          String ext = getFileExtension(file);

          //  첨부파일이 .zip 확장자일 경우 재귀적으로 압축을 해제하고, 내부에 있는 개별 파일을 LGFileMailVO로 변환해서 리턴
          if(ext.equalsIgnoreCase("zip")) {
            resultListC.add(itemVo);
          }
        }
    }
    return resultListC;
  }

  /***
   * 사이냅필터(외부 프로그램에서 호출)하여 파일에서 첨부파일 처리
   * @param resultMapList
   */
  private void enrichAttachBodies(List<Map<String, Object>> resultMapList) {
    for (Map<String, Object> map : resultMapList) {
      String attachPath = (String) map.get("attach_path");
      String attachName = (String) map.get("attach_name");

      if (isNullOrEmpty(attachPath) || isNullOrEmpty(attachName)) {
        map.put("attach_exist", "N");
        continue;
      }

      File attachFile = new File(attachPath);
      if (!attachFile.exists()) {
        map.put("attach_exist", "N");
        continue;
      }

      if (shouldExtractBody(attachName)) {
        String rawText = extractAttachBody(attachFile);
        String cleanText = cleanText(rawText);
        map.put("attach_body", cleanText);
      } else {
        map.put("attach_body", "");
      }

      map.put("attach_exist", "Y");
    }
  }

  private boolean shouldExtractBody(String fileName) {
    String lower = fileName.toLowerCase();
    return lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")
            || lower.endsWith(".ppt") || lower.endsWith(".pptx")
            || lower.endsWith(".xls") || lower.endsWith(".xlsx")
            || lower.endsWith(".hwp");
  }

  private boolean isNullOrEmpty(String str) {
    return str == null || str.trim().isEmpty();
  }


  public List<LGFileMailVO> splitByBodyLinks(List<LGFileMailVO> yesBodyLinkList) {
    List<LGFileMailVO> total = new ArrayList<>();

    for (LGFileMailVO originalVo : yesBodyLinkList) {
      List<String> bodyLinks = originalVo.getBodyLinks();

      if (bodyLinks == null || bodyLinks.isEmpty()) {
        continue;
      }

      for (int i = 0; i < bodyLinks.size(); i++) {
        String link = bodyLinks.get(i);

        String newKey = originalVo.getKey() + "_link_" + (i + 1);
        String newAttachId = originalVo.getAttach_id() + "_link_" + (i + 1);

        File file = new File(link);
        String fileName = file.getName();

        LGFileMailVO newVo = LGFileMailVO.builder()
                                         .key(newKey)
                                         .attach_id(newAttachId)
                                         .attach_path(link)
                                         .attach_name(fileName)
                                         .attach_exist("Y")
//                                         .from_bodylink("Y")
                                         .link_yn("Y")
                                         .attach_body("")               // ✔ 비움
                                         .attachFile(null)             // ✔ 제외
                                         // 이하 메일 메타데이터 복사
                                         .em_id(originalVo.getEm_id())
                                         .subject(originalVo.getSubject())
                                         .sender(originalVo.getSender())
                                         .senddtm(originalVo.getSenddtm())
                                         .receiver(originalVo.getReceiver())
                                         .idxdtm(originalVo.getIdxdtm())
                                         .em_body(originalVo.getEm_body())
                                         .email(originalVo.getEmail())
                                         .build();

        total.add(newVo);
      }
    }

    return total;
  }


  /***
   * 입력리스트로부터 메일본문(em_body)에서 링크 있는것 목록으로 뽑아 보관 ==> 입력인자의 결과목록에 값이 채워짐
   * @param inputList : 입력리스트
   * @param yesBodyLinkList  : 본문링크 있는 목록
   * @param noBodyLinkList : 본문링크 없는 목록
   * @throws IOException
   */
  private void processBodyLinks(
          List<LGFileMailVO> inputList,
          List<LGFileMailVO> yesBodyLinkList,
          List<LGFileMailVO> noBodyLinkList
  ) throws IOException {

    for (LGFileMailVO vo : inputList) {
      String emBody = vo.getEm_body();
      if (emBody == null || emBody.isBlank()) {
        noBodyLinkList.add(vo);
        continue;
      }

      boolean isHtml = ContentTypeDetector.isHtml(emBody);
      Set<String> urls = bodyParserService.extractUrls(emBody, isHtml);

      if (urls.isEmpty()) {
        noBodyLinkList.add(vo);
        continue;
      }

      String[] urlArray = urls.toArray(new String[0]);
      List<String> downloadedFiles = fileDownloadService.downloadFiles(urlArray);

      if (downloadedFiles.isEmpty()) {
        noBodyLinkList.add(vo);
      } else {
//        vo.setBodyLinks(downloadedFiles);
        vo.setLink_yn("Y");
        yesBodyLinkList.add(vo);
      }
    }
  }

  /***
   * 첨부파일과 매칭되지 않은 이메일들을 찾아서, 해당 이메일도 처리 대상으로 만들기 위해 LGFileMailVO 형태로 변환하여 리스트에 저장
   * @param
   * @param emailVos
   * @return
   */
  private List<LGFileMailVO> processEmailsWithoutAttachments(List<LGFileMailVO> fileMailVOS, List<LGEmailVo> emailVos) {
    List<LGFileMailVO> result = new ArrayList<>();

    for (LGEmailVo lgEmailVo : emailVos) {
      String mailGuid = lgEmailVo.getMailGUID();

      Boolean found = false;
      for(LGFileMailVO vo : fileMailVOS) {

        //이메일이 없는 파을은 매칭에서 skip
        if(vo.getEmail() == null) {
          continue;
        }

        if (mailGuid == vo.getEmail().getMailGUID()) {
          found = true;
        }
      }
        // 이미 첨부파일과 매칭된 메일은 스킵
        if (found) {
          continue;
        }
        // 첨부파일 없는 메일도 처리
        String key = "unknown_" + mailGuid;

        LGFileMailVO vo = LGFileMailVO.builder()
                                      .key(key)
                                      .attachFile(null)
                                      .email(lgEmailVo)
                                      .build();

        vo.fillDerivedFields(); // 파생 필드 채움
        result.add(vo);
      }

    return result;
  }


  /***
   * 이메일 JSON 경로에서 원본파일 읽음
   * @return  List<LGEmailVo>
   * @throws IOException
   */
  private List<LGEmailVo> loadEmails() throws IOException {
    File mailDir = new File(EMAILS_JSON_DIR);
    File[] jsonFiles = mailDir.listFiles(f -> f.getName().endsWith(".json"));
    List<LGEmailVo> emails = new ArrayList<>();
    if (jsonFiles != null) {
      for (File file : jsonFiles) {
        emails.addAll(objectMapper.readValue(file, new TypeReference<List<LGEmailVo>>() {}));
      }
    }
    return emails;
  }

  /***
   * 첨부 JSON 경로에서 원본파일 읽음
   * @return  List<LGEmailVo>
   * @throws IOException
   */
  private List<LGFileVO> loadFiles() throws IOException {
    File mailDir = new File(FILES_JSON_DIR);
    File[] jsonFiles = mailDir.listFiles(f -> f.getName().endsWith(".json"));
    List<LGFileVO> files = new ArrayList<>();
    if (jsonFiles != null) {
      for (File file : jsonFiles) {
        files.addAll(objectMapper.readValue(file, new TypeReference<List<LGFileVO>>() {}));
      }
    }
    return files;
  }

  /***
   * 고유한 MailGUID 만 뽑아 보관.
   * @param emails
   * @return   "MailGUID, 이메일" 형식으로 구성된 맵리턴 (모든 이메일정보 포함)
   */
  private Map<String, LGEmailVo> mapEmailsByGuid(List<LGEmailVo> emails) {
    return emails.stream().collect(Collectors.toMap(LGEmailVo::getMailGUID, e -> e));
  }

  /***
   * 파일경로의 파일정보를 읽은 파일VO를 순회하면서, List<파일메일VO>생성후, 데이터 채우고 리턴.
   * @param mailMap
   * @return
   * @throws IOException
   */
  private List<LGFileMailVO> processAttachments(Map<String, LGEmailVo> mailMap) throws IOException {
    File fileDir = new File(FILES_JSON_DIR);
    File[] jsonFiles = fileDir.listFiles(f -> f.getName().endsWith(".json"));
    List<LGFileMailVO> result = new ArrayList<>();
    if (jsonFiles != null) {
      for (File file : jsonFiles) {
        List<LGFileVO> attachList = objectMapper.readValue(file, new TypeReference<>() {});
        for (LGFileVO attach : attachList) {
          LGEmailVo email = mailMap.get(attach.getMailGUID());

          // 부모 이메일 찾은경우
          LGFileMailVO vo = LGFileMailVO.builder()
                                        .key(attach.getFileGUID() + "_" + attach.getMailGUID())
                                        .attachFile(attach)
                                        .email(email)
                                        .build();
          vo.fillDerivedFields();
          result.add(vo);
        }
      }
    }
    return result;
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
      List<LGFileMailVO> attachList = objectMapper.readValue(file, new TypeReference<>() {
      });
      File bulkFile = new File(BULK_PATH, "bulk_" + file.getName());

      try (BufferedWriter writer = new BufferedWriter(new FileWriter(bulkFile))) {
        for (LGFileMailVO attach : attachList) {
          String key = attach.getKey();
          String meta = String.format("{\"index\": {\"_index\": \"idx_email\", \"_id\": \"%s\"}}", key);
          writer.write(meta);
          writer.newLine();
          writer.write(objectMapper.writeValueAsString(objectMapper.valueToTree(attach)));
          writer.newLine();
        }
      }
      log.info("{} >> {} 변환 완료", file.getName(), bulkFile.getName());

      ///////////////////
      long attachCount = attachList.stream().map(LGFileMailVO::getAttach_id)     // attach_id 추출
                                   .filter(Objects::nonNull)        // null 값 방지
                                   .distinct()                      // 고유값으로 필터링
                                   .count()
              ;                        // 개수 세기

//      log.info("Uniq attach_id 개수: {}", attachCount);
      Set<String> uniqueAttachIds = attachList
              .stream()
              .map(LGFileMailVO::getAttach_id)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet())
              ;

//      log.info("Uniq attach_id 목록: {}", uniqueAttachIds);
      List<String> allList = uniqueAttachIds.stream().filter(id -> id != null && !id.isBlank())  // null 또는 공백 문자열 제외
                                            .collect(Collectors.toList())
              ;

      totalAttachIds.addAll(allList);         // 고유 attach_id를 누적시킴 :통계

      ////////////////////////
    } catch (IOException e) {
      log.error("bulk 파일 처리 중 오류 발생: {}", file.getName(), e);
    }
  }

  public List<Map<String, Object>> convertToMapList(List<LGFileMailVO> voList) {
    List<Map<String, Object>> mapList = new ArrayList<>();

    for (LGFileMailVO vo : voList) {
      Map<String, Object> map = new LinkedHashMap<>();

      map.put("key", vo.getKey());
      map.put("attach_id", vo.getAttach_id());
      map.put("attach_path", vo.getAttach_path());
      map.put("attach_parent", vo.getAttach_parent());
      map.put("from_zipfile", vo.getFrom_zipfile());
      map.put("attach_name", vo.getAttach_name());
      map.put("attach_body", vo.getAttach_body());
      map.put("attach_exist", vo.getAttach_exist());
      map.put("link_yn", vo.getLink_yn());
      map.put("em_id", vo.getEm_id());
      map.put("subject", vo.getSubject());
      map.put("sender", vo.getSender());
      map.put("senddtm", vo.getSenddtm());
      map.put("receiver", vo.getReceiver());
      map.put("idxdtm", vo.getIdxdtm());
      map.put("em_body", vo.getEm_body());
      map.put("bodyLinks", vo.getBodyLinks() != null ? vo.getBodyLinks() : new ArrayList<>());

      // nested VO 처리 (null 체크 포함)
      if (vo.getAttachFile() != null) {
        map.put("attachFile", vo.getAttachFile());
//        map.put("attachFile_fileName", vo.getAttachFile().getFileName());
//        map.put("attachFile_fileContentPath", vo.getAttachFile().getFileContentPath());
      }

      if (vo.getEmail() != null) {
        map.put("email", vo.getEmail());

//        map.put("email_mailGUID", vo.getEmail().getMailGUID());
//        map.put("email_mailSubject", vo.getEmail().getMailSubject());
//        map.put("email_mailFrom", vo.getEmail().getMailFrom());
//        map.put("email_mailSendTime", vo.getEmail().getMailSendTime());
//        map.put("email_mailTo", vo.getEmail().getMailTo());
//        map.put("email_mailClientContentPlain", vo.getEmail().getMailClientContentPlain());
      }

      mapList.add(map);
    }

    return mapList;
  }


  private String extractAttachBody(File file) {
    File tempFile = new File(file.getParent(), FileUtils.getFileNameOnly(file.getName()) + ".txt");
//    String raw = new ExternalConverter().convertWithExternalProgram(file.toString(), tempFile.toString());
    String raw = externalConverter.convertWithExternalProgram(file.toString(), tempFile.toString());

    if (tempFile.exists()) { // 필터링처리후, txt 파일 삭제
      tempFile.delete();
    }

    return cleanText(raw);
  }

  private String cleanText(String rawText) {
    return rawText
            .replaceAll("\r\n", "\n")
            .replaceAll("\n{1,}", " ")
            .replaceAll("(?m)^\\s+|\\s+$", "")
            .replaceAll(" {2,}", " ");
  }

  public List<Map<String, Object>> extractMapsWithBodylink(List<Map<String, Object>> resultList) {
    return resultList.stream().filter(map -> {
      Object bodylink = map.get("bodylink");
      return bodylink != null && !bodylink.toString().isBlank();
    }).collect(Collectors.toList());
  }




}
