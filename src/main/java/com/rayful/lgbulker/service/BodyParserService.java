package com.rayful.lgbulker.service;

import com.rayful.lgbulker.util.UrlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BodyParserService {

  public Set<String> extractUrls(String content, boolean isHtml) {
    if (isHtml) {
      return UrlExtractor.extractUrlsFromHtml(content);
    } else {
      return UrlExtractor.extractUrlsFromPlainText(content);
    }
  }
}