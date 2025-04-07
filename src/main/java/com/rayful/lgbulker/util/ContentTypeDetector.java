package com.rayful.lgbulker.util;

import org.jsoup.Jsoup;

public class ContentTypeDetector {

  /**
   * embody 문자열이 HTML인지 판별 (Jsoup 기반)
   * @param embody 입력 문자열
   * @return true면 HTML, false면 plain text
   */
  public static boolean isHtml(String embody) {
    if (embody == null || embody.trim().isEmpty()) {
      return false;
    }

    // Jsoup으로 파싱 후, 원본과 텍스트 내용이 다른 경우 HTML로 간주
    String textOnly = Jsoup.parse(embody).body().text();

    // 줄바꿈, 공백 등 제거하고 비교
    String strippedOriginal = embody.replaceAll("\\s+", "");
    String strippedTextOnly = textOnly.replaceAll("\\s+", "");

    return !strippedOriginal.equals(strippedTextOnly);
  }
}
