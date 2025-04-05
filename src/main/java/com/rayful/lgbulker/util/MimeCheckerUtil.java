package com.rayful.lgbulker.util;

import org.apache.tomcat.util.http.fileupload.util.mime.MimeUtility;

import java.io.*;

public class MimeCheckerUtil {

  /**
   * 주어진 .elm 파일이 MIME 인코딩되어 있는지 확인
   *
   * @param elmFile .elm 또는 .eml 파일
   * @return true = MIME 인코딩된 파일, false = 아닌 경우
   */
  public static boolean isMimeEncoded(File elmFile) {
    try (BufferedReader reader = new BufferedReader(new FileReader(elmFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim().toLowerCase();

        // MIME-Version 헤더 확인
        if (line.startsWith("mime-version")) {
          return true;
        }

        // Content-Transfer-Encoding 확인
        if (line.startsWith("content-transfer-encoding")) {
          return true;
        }

        // =?UTF-8?...?= 형식의 MIME 헤더 인코딩 확인
        if (line.contains("=?utf-8?b?") || line.contains("=?utf-8?q?")) {
          return true;
        }
      }
    } catch (IOException e) {
      System.err.println("파일 읽기 오류: " + e.getMessage());
    }
    return false;
  }


  /**
   * MIME 인코딩 문자열을 디코딩합니다.
   *
   * 예: =?UTF-8?B?7Jik7ZiV7KeE?=  → 디코딩 결과: "홍길동"
   *
   * @param encodedHeader MIME 인코딩 문자열
   * @return 디코딩된 문자열
   */
  public static String decodeMimeHeader(String encodedHeader) {
    try {
      return MimeUtility.decodeText(encodedHeader);
    } catch (UnsupportedEncodingException e) {
      System.err.println("디코딩 실패: " + e.getMessage());
      return encodedHeader; // 디코딩 실패 시 원본 반환
    }
  }

}
