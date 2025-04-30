package com.rayful.lgbulker.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rayful.lgbulker.entity.FileMetadata;
import com.rayful.lgbulker.enums.OCRErrorCode;
import com.rayful.lgbulker.mapper.FileMetadataMapper;
import com.rayful.lgbulker.vo.LGFileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

@Slf4j
@RequiredArgsConstructor
@Service
public class EmailAttachmentProcessService {

  @Value("${app.paths.synap_exe}")
  private String EXE_PATH;

  @Value("${ocr.output_path.image}")
  private String IMAGE_OUTPUT_PATH;

  @Value("${ocr.output_path.text}")
  private String TEXT_OUTPUT_PATH;


  @Value("${ocr.edentns.ocr_api_url}")
  private String OCR_API_URL;

  private final FileMetadataMapper fileMetadataMapper; // ✅ 추가, 에러시 DB에 로깅


  /**
   * 이메일 및 첨부파일 처리 로직
   * 첨부id존재하고 unknown이 아닌 문자열로 시작하는 것만 처리
   */
  public List<Map<String, Object>> processEmailAttachments(List<Map<String, Object>> emailDataList) throws IOException {

    List<Map<String, Object>> resultMapList = new ArrayList<>();

    for (Map<String, Object> emailData : emailDataList) {
      String emId = (String) emailData.get("em_id");
      String attachId = (String) emailData.get("attach_id");
      String attachName = (String) emailData.get("attach_name");
      String attachPath = (String) emailData.get("attach_path");


      if (attachPath == null || attachPath.isEmpty()) {
//        log.warn("첨부파일 경로가 null 또는 비어있습니다. em_id: {}, attach_id: {}", emailData.get("em_id"), emailData.get("attach_id"));
        resultMapList.add(emailData);
        continue; // ✅ 다음 파일로 넘어가기
      }

      //20250429 추가, 파일존재하지 않으면 로그에 추가.
      Path filePath = Paths.get(attachPath);

      if (!Files.exists(filePath)) {
        log.warn("첨부파일이 존재하지 않습니다. 경로: {}", attachPath);

        Object attachFileObj = emailData.get("attachFile");
        if (attachFileObj instanceof LGFileVO) {
          LGFileVO lgFileVO = (LGFileVO) attachFileObj;
          FileMetadata fileMetadata = FileMetadata
                  .builder()
                  .fileGuid((String) lgFileVO.getFileGUID())
                  .fileName((String) lgFileVO.getFileName())
                  .fileSize((Integer) Integer.parseInt(lgFileVO.getFileSize()))
                  .mailGuid((String) lgFileVO.getMailGUID())
                  .fileContentPath((String) lgFileVO.getFileContentPath())
                  .fileTime(parseToLocalDateTime(lgFileVO.getFileTime()))
                  .errorCode("404")
                  .errorMessage("첨부파일이 존재하지 않음")
                  .build()
                  ;

          fileMetadataMapper.insertFileMetadata(fileMetadata);

        } else {
          log.warn("attachFile 정보가 LGFileVO 타입이 아닙니다. em_id: {}, attach_id: {}", emailData.get("em_id"), emailData.get("attach_id"));
        }
      } else {

        //첨부id존재하고 unknown이 아닌 문자열로 시작하는 것만 처리
        // 파일존재 조건 추가
        if (attachId != null && !attachId.startsWith("unknown")) {
          if (isImageExtractableDocument(attachName)) { // 이미지를 추출해 작업할 파일
            extractFile2TextWithOcr(emailData, attachPath, emId, attachId);
//          System.out.println("본문추출1: " + emailData.get("attach_body"));
          } else if (isImageFile(attachName)) { // 이미지 파일
            extractImage2Text(emailData, attachPath, emId, attachId);
//          System.out.println("본문추출2: " + emailData.get("attach_body"));
          } else { // 그외 대상
            extractOtherFile2Text(emailData, attachPath, emId, attachId);
//          System.out.println("본문추출3: " + emailData.get("attach_body"));
          }
        }
      }

      resultMapList.add(emailData);
    }

    return resultMapList;
  }

  /**
   * 이메일 및 첨부파일 처리 로직
   */
  public List<Map<String, Object>> processEmailAttachments_elm(List<Map<String, Object>> emailDataList) throws IOException {

    List<Map<String, Object>> resultMapList = new ArrayList<>();

    for (Map<String, Object> emailData : emailDataList) {
      String emId = (String) emailData.get("em_id");
      String attachId = (String) emailData.get("attach_id");
      String attachName = (String) emailData.get("attach_name");
      String attachPath = (String) emailData.get("attach_path");

      //첨부id존재하고 unknown이 아닌 문자열로 시작하는 것만 처리
      if (attachId != null && !attachId.startsWith("unknown")) {
        if (isImageExtractableDocument(attachName)) { // 이미지를 추출해 작업할 파일
          extractFile2TextWithOcr(emailData, attachPath, emId, attachId);
//          System.out.println("본문추출1: " + emailData.get("attach_body"));
        } else if (isImageFile(attachName)) { // 이미지 파일
          extractImage2Text(emailData, attachPath, emId, attachId);
//          System.out.println("본문추출2: " + emailData.get("attach_body"));
        } else { // 그외 대상
          extractOtherFile2Text(emailData, attachPath, emId, attachId);
//          System.out.println("본문추출3: " + emailData.get("attach_body"));
        }
      }
      resultMapList.add(emailData);
    }

    return resultMapList;
  }

  /****************************************************************************
   * 문서 내 이미지를 추출후 OCR 호출하여 제공받은 텍스트를 문서내용과 Merge 하여 본문저장
   **************************************************************************/
  private void extractFile2TextWithOcr(Map<String, Object> emailData, String filePath, String emId, String attachId) {
    System.out.println("call extractFile2TextWithOcr(): " + filePath);

    try {
      // 출력 디렉토리 생성
      File outputDir = new File(IMAGE_OUTPUT_PATH + File.separator + emId + "_" + attachId);
      if (!outputDir.exists()) {
        outputDir.mkdirs();
      }

      File contentFile = new File(outputDir, "content.txt");

      // 사이냅필터 사용시 옵션 -ei를 주어 이미지까지 추출...
      ProcessBuilder processBuilder = new ProcessBuilder(EXE_PATH, "-O", "-ei", IMAGE_OUTPUT_PATH + "/" + emId + "_" + attachId, filePath);
      processBuilder.redirectErrorStream(true); // 에러 출력도 통합
      Process process = processBuilder.start();

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("MS949"))); BufferedWriter writer = new BufferedWriter(new FileWriter(contentFile))) {
        String line;
        while ((line = reader.readLine()) != null) {
          writer.write(line);
          writer.newLine();
        }
      }

      int exitCode = process.waitFor();
      System.out.println("추출 완료: " + filePath + " (exitCode: " + exitCode + ")");

      //**********************************************
      // 본문에서 이미지 OCR 처리
      //**********************************************
      String updatedBody = replaceImageIndexesWithOCR(readFileContent(contentFile), emId, attachId, emailData);

      // OCR 처리된 내용 다시 파일에 저장
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(contentFile))) {
        writer.write(updatedBody);
      }

      // attach_body 필드에 전체 추출내용 put
      emailData.put("attach_body", updatedBody);
    } catch (Exception e) {
      System.err.println("문서 추출 중 오류 발생: " + e.getMessage());
    }
  }

  /**
   * 이미지파일을 OCR API를 호출하여 제공받은 텍스트를 본문저장
   */
  private void extractImage2Text(Map<String, Object> emailData, String filePath, String emId, String attachId) throws IOException {
    System.out.println("call extractImage2Text(): " + filePath);

    String extractedText = callOCRApi(filePath, emailData);  // 현재 정의된 메서드에 맞게 호출
    // attach_body 필드에 전체 추출내용 put
    emailData.put("attach_body", extractedText);
  }

  /**
   * 기타 문서내용을 본문저장
   */
  private void extractOtherFile2Text(Map<String, Object> emailData, String filePath, String emId, String attachId) {
    System.out.println("call extractOtherFile2Text(): " + filePath);

    try {
      // 출력 디렉토리 생성
      File outputDir = new File(TEXT_OUTPUT_PATH + File.separator + emId + "_" + attachId);
      if (!outputDir.exists()) {
        outputDir.mkdirs();
      }

      File contentFile = new File(outputDir, "content.txt");

      ProcessBuilder processBuilder = new ProcessBuilder(EXE_PATH, filePath);
      processBuilder.redirectErrorStream(true); // 에러 출력도 통합
      Process process = processBuilder.start();

      Charset encoding = isWindows() ? Charset.forName("MS949") : StandardCharsets.UTF_8;

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), encoding)); BufferedWriter writer = new BufferedWriter(new FileWriter(contentFile))) {
        String line;
        while ((line = reader.readLine()) != null) {
          writer.write(line);
          writer.newLine();
        }
      }

      int exitCode = process.waitFor();
      System.out.println("추출 완료: " + filePath + " (exitCode: " + exitCode + ")");

      // 본문 추출 내용 출력
      emailData.put("attach_body", readFileContent(contentFile));
    } catch (Exception e) {
      System.err.println("문서 추출 중 오류 발생: " + e.getMessage());
    }
  }

  /**
   * 문서추출 내용중 특정 문자열 패턴을 이미지에서 추출한 텍스트와 replace 한다.
   */
 /* private String replaceImageIndexesWithOCR(String text, String emId, String attachId) {
    Pattern pattern = Pattern.compile("\\{\\.\\.IMAGE_INDEX:(\\d+)\\}");
    Matcher matcher = pattern.matcher(text);
    StringBuffer result = new StringBuffer();

    while (matcher.find()) {
      String imageIndex = matcher.group(1);
      String imagePath = IMAGE_OUTPUT_PATH + File.separator + emId + "_" + attachId + File.separator + "img" + imageIndex + ".png";
      String ocrText = callOCRApi(imagePath);

      matcher.appendReplacement(result, Matcher.quoteReplacement(ocrText));
    }
    matcher.appendTail(result);

    return result.toString();
  }
*/
  private String replaceImageIndexesWithOCR(String text, String emId, String attachId, Map<String, Object> emailData) throws IOException {
    Pattern pattern = Pattern.compile("\\{\\.\\.IMAGE_INDEX:(\\d+)\\}");
    Matcher matcher = pattern.matcher(text);
    StringBuffer result = new StringBuffer();
    String baseDir = IMAGE_OUTPUT_PATH + "/" + emId + "_" + attachId;
    String[] allowedExtensions = {"jpg", "jpeg", "png", "tiff", "bmp"};
    while (matcher.find()) {
      String imageIndex = matcher.group(1);
      String imagePrefix = "img" + imageIndex;
      String imageFilePath = null;
      File dir = new File(baseDir);
      if (dir.exists() && dir.isDirectory()) {
        File[] matchingFiles = dir.listFiles((d, name) -> {
          if (!name.startsWith(imagePrefix + ".")) return false;
          String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
          for (String allowed : allowedExtensions) {
            if (allowed.equals(ext)) return true;
          }
          return false;
        });
        if (matchingFiles != null && matchingFiles.length > 0) {
          imageFilePath = matchingFiles[0].getAbsolutePath();  // 첫 번째 매칭 파일
        }
      }
      String ocrText = "";
      if (imageFilePath != null ) {
        ocrText = callOCRApi(imageFilePath, emailData);
      } else {
        ocrText = "[이미지 없음: " + imageIndex + "]";
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(ocrText));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /**
   * 문서내용이 저장된 txt 파일을 read 한다.
   */
  private static String readFileContent(File file) {
    StringBuilder content = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = reader.readLine()) != null) {
        content.append(line).append("\n");
      }
    } catch (IOException e) {
      System.err.println("파일 읽기 오류: " + e.getMessage());
    }
    return content.toString();
  }

  /*****************************************************
   * OCR API를 호출하여 이미지에서 추출한 텍스트를 반환한다.  20250430 파라미터 emailData 추가
   *************************************************/
  private String callOCRApi(String imagePath, Map<String, Object> emailData) throws IOException {
//    System.out.println("OCR API 호출: " + imagePath);

    URL url = new URL(OCR_API_URL);
    HttpURLConnection connection = null;

    File file = new File(imagePath);

    try {

      connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(10_000); // ✅ 연결 타임아웃 10초 설정
      connection.setReadTimeout(10_000);    // ✅ 응답 읽기 타임아웃 10초 설정
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setDoOutput(true);

      if (getFileExtension(file.getName()).equalsIgnoreCase("bmp")) {
        log.info("{}, 파일 형식 변환 bmp --> jpg", file.getName());
        file = convertBmpToJpg(file);
      }
      byte[] fileContent = Files.readAllBytes(file.toPath());
      String base64Encoded = Base64.getEncoder().encodeToString(fileContent);

      // JSON 요청 문자열을 빌더 형식으로 생성
      String requestId = String.valueOf(UUID.randomUUID());
      StringBuilder jsonBuilder = new StringBuilder();
      jsonBuilder
              .append("{")
              .append("\"version\":\"V1\",")
              .append("\"requestId\":\"")
//              .append(UUID.randomUUID())
              .append(requestId)
              .append("\",")
              .append("\"timestamp\":")
              .append(System.currentTimeMillis())
              .append(",")
              .append("\"images\":[{")
              .append("\"format\":\"")
              .append(getFileExtension(file.getName()))
              .append("\",")
              .append("\"data\":\"")
              .append(base64Encoded)
              .append("\",")
              .append("\"name\":\"")
              .append(file.getName())
              .append("\",")
              .append("\"option\":{\"pageRange\":[0]}")
              .append("}],")
              .append("\"details\":\"text\"")
              .append("}")
      ;

      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setDoOutput(true);

      try (OutputStream os = connection.getOutputStream()) {
        byte[] input = jsonBuilder.toString().getBytes(StandardCharsets.UTF_8);
        os.write(input);
      }

      // 응답 처리 - 정상응답
      if (connection.getResponseCode() == 200) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
          StringBuilder response = new StringBuilder();
          String line;
          while ((line = reader.readLine()) != null) {
            response.append(line);
          }
//          log.info("==== ocr response : {} ==================", response);
          return "[[IMAGE TEXT : " + parseOcrTextFromResponse(response.toString()) + "]]";
        }
      } else {
        System.err.println("OCR API 호출 실패 (HTTP " + connection.getResponseCode() + ")");

        OCRErrorCode ocrErrorCode = OCRErrorCode.OCR_API_CALL_FAIL;

        Object attachFileObj = emailData.get("attachFile");
        if (attachFileObj instanceof LGFileVO) {
          LGFileVO lgFileVO = (LGFileVO) attachFileObj;
          String mailGuid = lgFileVO.getMailGUID();
          String fileGuid = lgFileVO.getFileGUID();

          // ✅ 실패한 파일 정보를 DB에 insert
//          fileMetadataMapper.insertFileMetadata(FileMetadata
//                  .builder()
//                  .fileGuid(requestId)
//                  .fileName(file.getName())
//                  .fileSize(null)
//                  .mailGuid(lgFileVO.getMailGUID()) // 예시로 실패 고정
//                  .fileContentPath(imagePath)
//                  .fileTime(LocalDateTime.now())
//                  .errorCode(error.getCode())
//                  .errorMessage(error.getMessage())
//                  .build());
          logFailureToDB(file, imagePath, ocrErrorCode, fileGuid, mailGuid);

        }
        return "[OCR_ERROR]";
      }

    } catch (Exception e) {
      System.err.println("OCR API 요청 중 오류 발생: " + e.getMessage());

      OCRErrorCode ocrErrorCode = OCRErrorCode.OCR_API_PROCESS_FAIL;

      Object attachFileObj = emailData.get("attachFile");
      if (attachFileObj instanceof LGFileVO) {
        LGFileVO lgFileVO = (LGFileVO) attachFileObj;
        String mailGuid = lgFileVO.getMailGUID();
        String fileGuid = lgFileVO.getFileGUID();

//      fileMetadataMapper.insertFileMetadata(FileMetadata
//              .builder()
//              .fileGuid(UUID.randomUUID().toString())
//              .fileName(file.getName())
//              .fileSize(null)
//              .mailGuid("ocr_fail") // 예시로 실패 고정
//              .fileContentPath(imagePath)
//              .fileTime(LocalDateTime.now())
//              .errorCode(error.getCode())
//              .errorMessage(error.getMessage())
//              .build());

        logFailureToDB(file, imagePath, ocrErrorCode, fileGuid, mailGuid);
      }

      return "[OCR_ERROR]";
    }
  }

  /**
   *  ocr api 호출 시 리턴 받은 response를 파싱하여 문자열로 리턴
   */
  private static String parseOcrTextFromResponse(String jsonResponse) {

    if ("{}".equals(jsonResponse)) {
      return "";
    }

    StringBuilder extractedText = new StringBuilder();
    try {
      JsonObject obj = JsonParser.parseString(jsonResponse).getAsJsonObject();
      JsonArray result = obj.getAsJsonArray("result");

      for (JsonElement pageElem : result) {
        JsonObject page = pageElem.getAsJsonObject();
        JsonArray lines = page.getAsJsonArray("lines");

        for (JsonElement lineElem : lines) {
          JsonObject line = lineElem.getAsJsonObject();
          JsonArray words = line.getAsJsonArray("words");

          for (JsonElement wordElem : words) {
            JsonObject word = wordElem.getAsJsonObject();
            extractedText.append(word.get("text").getAsString()).append(" ");
          }
          extractedText.append("\n");
        }
      }
    } catch (Exception e) {
      System.err.println("OCR 응답 파싱 오류: " + e.getMessage());
      return "[OCR_PARSE_ERROR]";
    }
    return extractedText.toString().trim();
  }

  /**
   * 파일 확장자 추출
   */
  private static String getFileExtension(String fileName) {
    int dotIndex = fileName.lastIndexOf('.');
    return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1).toLowerCase();
  }

  /**
   * 이미지 추출 대상 문서 파일인지 확인하는 메서드
   */
  private static boolean isImageExtractableDocument(String fileName) {
    return fileName.toLowerCase().matches(".*\\.(hwp|hwpx|doc|docx|ppt|pptx|xls|xlsx|pdf)$");
  }

  /**
   * 이미지 파일인지 확인하는 메서드
   */
  private static boolean isImageFile(String fileName) {
    return fileName.toLowerCase().matches(".*\\.(jpg|jpeg|png|tiff|bmp)$");
  }

  /**
   * 윈도우 여부 확인하는 메서드
   */
  private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  public static File convertBmpToJpg(File bmpFile) throws IOException {

    BufferedImage image = ImageIO.read(bmpFile);
    File jpgFile = new File(bmpFile.getParent(), bmpFile.getName().replaceAll("\\.bmp$", ".jpg"));
    ImageIO.write(image, "jpg", jpgFile);
    return jpgFile;
  }

  private LocalDateTime parseToLocalDateTime(String fileTimeStr) {
    if (fileTimeStr == null || fileTimeStr.isEmpty()) {
      return LocalDateTime.now();
    }

    try {
      return LocalDateTime.parse(fileTimeStr); // ✅ 포맷 지정 없이 parse!
    } catch (Exception e) {
      return LocalDateTime.now();
    }
  }

  private void logFailureToDB(File file, String imagePath, OCRErrorCode errorCode, String fileGuid, String mailGuid) {
    try {
      fileMetadataMapper.insertFileMetadata(
              FileMetadata.builder()
                          .fileGuid(fileGuid)
                          .fileName(file.getName())
                          .fileSize(file.exists() ? (int) file.length() : null)
                          .mailGuid(mailGuid)
                          .fileContentPath(imagePath)
                          .fileTime(LocalDateTime.now())
                          .errorCode(errorCode.getCode())
                          .errorMessage(errorCode.getMessage())
                          .build()
      );
      log.warn("❌ OCR 실패 기록 DB 저장 완료. 파일명: {}", file.getName());
    } catch (Exception dbEx) {
      log.error("❌ OCR 실패 기록 저장 중 오류 발생", dbEx);
    }
  }



}
