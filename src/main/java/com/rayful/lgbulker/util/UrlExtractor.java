package com.rayful.lgbulker.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlExtractor {

  // 공통 URL 정규식 패턴
  private static final Pattern URL_PATTERN = Pattern.compile(
          "(https?://[\\w\\-\\.\\?\\,\\'/\\\\\\+&%\\$#_=:@]+)");

  // HTML content에서 URL 추출
  public static Set<String> extractUrlsFromHtml(String html) {
    Set<String> urls = new HashSet<>();

    Document doc = Jsoup.parse(html);

    // <a href="..."> 같은 링크 추출
    Elements links = doc.select("a[href]");
    for (Element link : links) {
      urls.add(link.attr("abs:href"));
    }

    // src 속성 (예: 이미지, iframe 등)
    Elements srcElements = doc.select("[src]");
    for (Element el : srcElements) {
      urls.add(el.attr("abs:src"));
    }

    // style 태그나 인라인 스타일 내 URL (예: background-image)
    Matcher matcher = URL_PATTERN.matcher(doc.text());
    while (matcher.find()) {
      urls.add(matcher.group());
    }

    return urls;
  }

  // plain text content에서 URL 추출
  public static Set<String> extractUrlsFromPlainText(String text) {
    Set<String> urls = new HashSet<>();

    Matcher matcher = URL_PATTERN.matcher(text);
    while (matcher.find()) {
      urls.add(matcher.group());
    }

    return urls;
  }

}
