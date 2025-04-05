package com.rayful.lgbulker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rayful.lgbulker.vo.EmailDto;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

@Service
public class LinkExtractorService {

  private static final Pattern URL_PATTERN = Pattern.compile(
          "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
          Pattern.CASE_INSENSITIVE);

  public List<String> extractAttachLinksFromJson(File jsonFile) {
    List<String> attachLinks = new ArrayList<>();
    ObjectMapper mapper = new ObjectMapper();

    try {
      List<EmailDto> emailList = mapper.readValue(
              jsonFile,
              new TypeReference<>() {}
      );

      for (EmailDto email : emailList) {
        if (email.getBody() != null) {
          Matcher matcher = URL_PATTERN.matcher(email.getBody());
          while (matcher.find()) {
            attachLinks.add(matcher.group());
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

    return attachLinks;
  }

  public List<String> downloadLinksToLocal(List<String> links, String downloadDirPath) {
    List<String> downloadList = new ArrayList<>();
    Path downloadDir = Paths.get(downloadDirPath);

    try {
      if (!Files.exists(downloadDir)) {
        Files.createDirectories(downloadDir);
      }

      for (String link : links) {
        try {
          URL url = new URL(link);
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();
          connection.setRequestMethod("GET");
          connection.setInstanceFollowRedirects(true);

          int responseCode = connection.getResponseCode();
          if (responseCode != HttpURLConnection.HTTP_OK) {
            System.err.println("❌ 응답 실패 (" + link + "): " + responseCode);
            continue;
          }

          // ✅ 파일명 결정
          String fileName = resolveFileName(connection, link);
          Path filePath = downloadDir.resolve(fileName);

          try (InputStream in = connection.getInputStream();
               FileOutputStream out = new FileOutputStream(filePath.toFile())) {
            in.transferTo(out);
          }

          downloadList.add(filePath.toString());
          System.out.println("✅ 다운로드 완료: " + filePath);

        } catch (Exception e) {
          System.err.println("❌ 다운로드 실패 (" + link + "): " + e.getMessage());
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

    return downloadList;
  }

  private String getFileNameFromDisposition(String disposition) {
    try {
      if (disposition == null) return null;

      // filename*=UTF-8''%EC%8A%A4%ED%80%B4%EC%8B%9C.zip
      if (disposition.contains("filename*=")) {
        String encodedPart = disposition.split("filename\\*=")[1].trim();
        // Remove encoding spec: UTF-8''
        if (encodedPart.startsWith("UTF-8''")) {
          encodedPart = encodedPart.substring(7);
        }
        // URL decode
        String decoded = URLDecoder.decode(encodedPart, UTF_8);
        return sanitizeFilename(decoded);
      }

      // filename="xxx.zip"
      if (disposition.contains("filename=")) {
        String raw = disposition.split("filename=")[1].trim().replaceAll("\"", "");
        return sanitizeFilename(raw);
      }

    } catch (Exception e) {
      System.err.println("❗️파일명 파싱 오류: " + e.getMessage());
    }

    return null;
  }

  private String sanitizeFilename(String filename) {
    return filename.replaceAll("[\\\\/:*?\"<>|]", "_");  // 윈도우 금지 문자 대체
  }




  private String resolveFileName(HttpURLConnection connection, String urlStr) {
    // 1. Content-Disposition에서 추출 시도
    String disposition = connection.getHeaderField("Content-Disposition");
    String fileName = getFileNameFromDisposition(disposition);

    if (fileName != null && !fileName.isBlank()) {
      return fileName;
    }

    // 2. fallback: URL 경로에서 파일명 추출
    try {
      URL url = new URL(urlStr);
      String path = url.getPath(); // /static/.../btn_download.png
      return Paths.get(path).getFileName().toString(); // btn_download.png
    } catch (Exception e) {
      // 3. 그래도 안 되면 UUID로
      return UUID.randomUUID() + ".bin";
    }
  }




  // 테스트용 main (Spring Boot 테스트 대체 가능)
  public static void main(String[] args) {
    LinkExtractorService service = new LinkExtractorService();
    File jsonFile = Paths.get("D:/1.data_poc/input/emails/20250328_60748494_대용량_emails.json").toFile();

    List<String> links = service.extractAttachLinksFromJson(jsonFile);
    links.forEach(System.out::println);

    List<String> downloadList = service.downloadLinksToLocal(links, "D:/1.data_poc/input/downloaded_links");
    downloadList.forEach(System.out::println);
    System.out.println("총 다운로드된 파일 수: " + downloadList.size());



  }
}

