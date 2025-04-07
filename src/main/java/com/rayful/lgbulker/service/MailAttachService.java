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

  //  private final EmailMapper emailMapper;
//첨부파일 압축해제 unzip
  private final FileService fileService;
  //외부필터 사용.
  private final ExternalConverter externalConverter;

  private final BulkFileWriter bulkFileWriter;

  private final  BodyParserService bodyParserService;

  //로그 수집위해 사용.
  BulkerLogVO bulkerLogVO = new BulkerLogVO();

  int Total_EmCount;    // 총 처리대상 이메일 카운트
  int Total_AttachCount;    // 총 처리대상 이메일 카운트

  List<String> totalAttachIds = new ArrayList<>(); //총 처리대상 첨부파일 id

  private RestTemplate restTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();
//  private final String mailJsonPath = "src/main/resources/data/20250404_08_25_mail_0.json";
//  private final String attachDirPath = "src/main/resources/data/attach";

  private final List<LGFileMailVO> result = new ArrayList<>();

  public void load() {
    try {
      // 1. 메일 로드
      // ✅ 1. 메일 JSON 여러 개 로딩
      List<LGEmailVo> mails = new ArrayList<>();
      File mailDir = new File(EMAILS_JSON_DIR); // 메일 저장경로
      File[] mailFiles = mailDir.listFiles((dir, name) -> name.endsWith(".json"));

      if (mailFiles != null) {
        for (File file : mailFiles) {
          List<LGEmailVo> mailListInFile = objectMapper.readValue(file, new TypeReference<List<LGEmailVo>>() {
          });
          mails.addAll(mailListInFile);
        }
      }

      Map<String, LGEmailVo> mailMap = mails.stream().collect(Collectors.toMap(LGEmailVo::getMailGUID, mail -> mail));

      Set<String> matchedMailGuids = new HashSet<>();

      // 2. 첨부파일 로드 (파일당 List<AttachFileVo>)
      File fileDir = new File(FILES_JSON_DIR);  //첨부파일 저장경로
      File[] files = fileDir.listFiles((dir, name) -> name.endsWith(".json"));

      List<LGFileVO> fileVOList = new ArrayList<>();

      if (files != null) {
        for (File file : files) {

          fileVOList = objectMapper.readValue(file, new TypeReference<List<LGFileVO>>() {
          });


          //첨부기준으로 실행.
          for (LGFileVO attach : fileVOList) {
            //파일의 mailGuid 찾아 matchedMailGuids 추가 및 키생성, 첨부_메일기준 VO객체 생성
            String mailGuid = attach.getMailGUID();
            LGEmailVo matchedMail = mailMap.get(mailGuid);
            matchedMailGuids.add(mailGuid);

            //첨부파일 존재확인 및 압축파일이면 압축해제로직시작

            String key = attach.getFileGUID() + "_" + mailGuid;

            LGFileMailVO vo = LGFileMailVO.builder().key(key).attachFile(attach).email(matchedMail).build();

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

          LGFileMailVO vo = LGFileMailVO.builder().key(key).attachFile(null).email(mail).build();

          vo.fillDerivedFields(); // ✅ 파생 필드 세팅
          result.add(vo);
        }
      }

      // 파일이 압축파일이면 unzip 처리
      List<LGFileMailVO> processedList = fileService.checkFile_Unzip_if_Zipfile(result);

      //----------------------------------------------------------------------
      //서상호 차장에게 텍스트 필터 처리요청형식으로 변환
      //----------------------------------------------------------------------
      List<Map<String, Object>> finalMapList = convertToMapList(processedList);

      // 사이냅 필터통한 첨부파일 텍스트 추출--자체

      for (Map<String, Object> resultMap : finalMapList) {
        String mAttach_path = (String) resultMap.get("attach_path");
        String mAttach_name = (String) resultMap.get("attach_name");

        // ✅ null 또는 빈 값이면 바로 처리하고 다음으로
        if (mAttach_path == null || mAttach_path.isBlank() ||
                mAttach_name == null || mAttach_name.isBlank()) {
          resultMap.put("attach_exist", "N");
          continue;
        }

        File mAttachFile = new File(mAttach_path);

        if (!mAttachFile.exists()) {
          resultMap.put("attach_exist", "N");
          continue;
        }

        // ✅ 파일이 존재하고 확장자 검사
        String filteredText = "";
        String cleanText = "";

        if (mAttach_name.endsWith(".pdf")
                || mAttach_name.endsWith(".doc") || mAttach_name.endsWith(".docx")
                || mAttach_name.endsWith(".ppt") || mAttach_name.endsWith(".pptx")
                || mAttach_name.endsWith(".xls") || mAttach_name.endsWith(".xlsx")
                || mAttach_name.endsWith(".hwp"))
        {
          filteredText = extractAttachBody(mAttachFile);
          cleanText = cleanText(filteredText);
        } else {
          cleanText = "";
        }

        resultMap.put("attach_exist", "Y");
        resultMap.put("attach_body", cleanText);

      }

      // 4. 본문에서 링크 추출 및 첨부파일다운로드 확인
      for (Map<String, Object> resultMap : finalMapList) {
        Boolean isHtml = false;
        Boolean isPlain  = false;;
        String emBody = resultMap.get("em_body").toString();

        isHtml = ContentTypeDetector.isHtml(emBody);
        isPlain = ContentTypeDetector.isHtml(emBody); // false

        if(isHtml) {
          Set<String> urlsFromHtml = bodyParserService.extractUrls(emBody, true);
          String[] urls = (String[]) urlsFromHtml.toArray();
//          링크추출
//          파일다운로드
          if(!urlsFromHtml.isEmpty()) {
            FileDownloadService downloader = new FileDownloadService();
            List<AttachVO> downloadedFiles = downloader.downloadFiles(urls);
            System.out.println("********************************");
            downloadedFiles.forEach(System.out::println);

          }

        } else {
          Set<String> urlsFromPlain = bodyParserService.extractUrls(emBody, false);
          if(!urlsFromPlain.isEmpty()) {
            // Set → 배열로 변환
            String[] urls = urlsFromPlain.toArray(new String[0]);

            FileDownloadService downloader = new FileDownloadService();
            List<AttachVO> downloadedFiles = downloader.downloadFiles(urls);

            System.out.println("============ 다운로드 결과 ============");
            downloadedFiles.forEach(System.out::println);
          }
        }
      }


      // 통합형식의 json 파일 저장 : bulk파일로 변환전.
      bulkFileWriter.writeAsBulkJsonFiles(finalMapList, MERGED_DIR);

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

}
