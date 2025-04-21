package com.rayful.lgbulker.service;

import com.rayful.lgbulker.vo.AttachVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FileDownloadService {

  @Value("${app.paths.input.download}")
  private String DOWNLOAD_DIR;

  //실제 파일을 다운로드 받아 처리 다운로드가 완료되면 처리수행함.
  public List<String> downloadFiles(String[] urls) throws IOException {
    List<String> attachList = new ArrayList<>();

    Path downloadDir = Path.of(DOWNLOAD_DIR);
    if (!Files.exists(downloadDir)) {
      Files.createDirectories(downloadDir);
    }

    for (String urlString : urls) {
      HttpURLConnection connection = null;
      try {
        URL url = new URL(urlString);
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {

          // Content-Disposition 헤더에서 파일명 추출
          String contentDisposition = connection.getHeaderField("Content-Disposition");
          String fileName = getFileNameFromHeaderOrUrl(contentDisposition, urlString);

          // 다운로드할 파일이 이미지, 오피스, pdf, hwp, zip 인경우에만 다운로드 수행함.
          if (fileName.endsWith(".doc") || fileName.endsWith(".docx") || fileName.endsWith(".ppt")
                  || fileName.endsWith(".pptx") || fileName.endsWith(".xls") || fileName.endsWith(".xlsx")
                  || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") || fileName.endsWith(".bmp")
                  || fileName.endsWith(".tiff ") || fileName.endsWith(".hwp") || fileName.endsWith(".zip")
                  || fileName.endsWith(".pdf")) {

            Path targetPath = downloadDir.resolve(fileName);

            try (InputStream inputStream = connection.getInputStream()) {
              Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            long fileSize = Files.size(targetPath);
            attachList.add(targetPath.toString());

          }
        } else {
          System.err.println("응답 오류: " + urlString + " - " + responseCode);
        }

      } catch (IOException e) {
        System.err.println("다운로드 실패: " + urlString + " -> " + e.getMessage());
      } finally {
        if (connection != null) {
          connection.disconnect();
        }
      }
    }

    return attachList;
  }

  // Content-Disposition 헤더 또는 URL에서 파일명 추출
  private String getFileNameFromHeaderOrUrl(String contentDisposition, String url) {
    if (contentDisposition != null) {
      try {
        if (contentDisposition.contains("filename*=")) {
          // RFC 5987 형식: filename*=UTF-8''%ED%95%9C%EA%B8%80.zip
          Pattern pattern = Pattern.compile("filename\\*=(?:UTF-8'')?([^;\\s]+)");
          Matcher matcher = pattern.matcher(contentDisposition);
          if (matcher.find()) {
            String encoded = matcher.group(1).trim();
            String decoded = URLDecoder.decode(encoded, "UTF-8");
            return sanitizeFilename(decoded);
          }
        } else if (contentDisposition.contains("filename=")) {
          // 일반 형식: filename="한글이름.zip" (ISO-8859-1 → UTF-8 변환 필요)
          Pattern pattern = Pattern.compile("filename=\"?([^\";]+)\"?");
          Matcher matcher = pattern.matcher(contentDisposition);
          if (matcher.find()) {
            String isoFilename = matcher.group(1).trim();
            String utf8Filename = new String(isoFilename.getBytes("ISO-8859-1"), "UTF-8");
            return sanitizeFilename(utf8Filename);
          }
        }
      } catch (Exception e) {
        System.err.println("파일명 파싱 실패: " + e.getMessage());
      }
    }

    return extractFileNameFromUrl(url);
  }

  // URL에서 파일명 추출 (fallback)
  private String extractFileNameFromUrl(String url) {
    try {
      String decoded = URLDecoder.decode(url, "UTF-8");
      String[] parts = decoded.split("[/\\\\?]");
      String last = parts[parts.length - 1];
      return (last.isEmpty() || last.length() > 100) ? UUID.randomUUID() + ".bin" : sanitizeFilename(last);
    } catch (Exception e) {
      return UUID.randomUUID() + ".bin";
    }
  }

  // 파일 이름에 사용할 수 없는 문자를 제거
  private String sanitizeFilename(String filename) {
    return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
  }
}
