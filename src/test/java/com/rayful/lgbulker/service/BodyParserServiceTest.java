package com.rayful.lgbulker.service;

import com.rayful.lgbulker.util.ContentTypeDetector;
import com.rayful.lgbulker.util.UrlExtractor;
import com.rayful.lgbulker.vo.AttachVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class BodyParserServiceTest {

  public Set<String> extractUrls(String content, boolean isHtml) {
    if (isHtml) {
      return UrlExtractor.extractUrlsFromHtml(content);
    } else {
      return UrlExtractor.extractUrlsFromPlainText(content);
    }
  }

  @Test
  void parseBodyContent() {


    String htmlContent = "<html><body><a href=\"https://bigfile.mail.naver.com/download?fid=g/RcWrC516cXK3YwHquZKqE9KIYZKAU9KAKqKxulKx2dFIYwFqtrKAU9KxvlHqMqFqvlKztrMqU9KqMrpxt9KxvwFqiopAvmFAgqpzkv\">Link</a></body></html>";
    String plainTextContent = "Check this link: https://bigfile.mail.naver.com/download?fid=g/RcWrC516cXK3YwHquZKqE9KIYZKAU9KAKqKxulKx2dFIYwFqtrKAU9KxvlHqMqFqvlKztrMqU9KqMrpxt9KxvwFqiopAvmFAgqpzkv and http://test.org";

    BodyParserService bodyParserService = new BodyParserService();
    Set<String> urlsFromHtml = bodyParserService.extractUrls(htmlContent, true);
    Set<String> urlsFromPlain = bodyParserService.extractUrls(plainTextContent, false);

    System.out.println("===================================");
    System.out.println("HTML URLs: " + urlsFromHtml);
    System.out.println("Plain Text URLs: " + urlsFromPlain);
    System.out.println("===================================");

  }


  public static void main(String[] args) throws IOException {
//
//      String html = "<p>안녕하세요 <strong>케빈</strong>님!</p>";
//      String plain = "안녕하세요 케빈님!";

    String htmlContent = " <html><body> <p>안녕하세요 <strong>케빈</strong>님!</p>  <a href=\"https://bigfile.mail.naver.com/download?fid=g/RcWrC516cXK3YwHquZKqE9KIYZKAU9KAKqKxulKx2dFIYwFqtrKAU9KxvlHqMqFqvlKztrMqU9KqMrpxt9KxvwFqiopAvmFAgqpzkv\">Link</a></body></html>";
    String plainTextContent = "Check this link: https://bigfile.mail.naver.com/download?fid=g/RcWrC516cXK3YwHquZKqE9KIYZKAU9KAKqKxulKx2dFIYwFqtrKAU9KxvlHqMqFqvlKztrMqU9KqMrpxt9KxvwFqiopAvmFAgqpzkv and http://test.org";

    BodyParserService bodyParserService = new BodyParserService();
    Set<String> urlsFromHtml = bodyParserService.extractUrls(htmlContent, true);
    Set<String> urlsFromPlain = bodyParserService.extractUrls(plainTextContent, false);

    System.out.println("===================================");
    System.out.println("HTML URLs: " + urlsFromHtml);
    System.out.println("Plain Text URLs: " + urlsFromPlain);
    System.out.println("===================================");

    System.out.println("html? " + ContentTypeDetector.isHtml(htmlContent));   // true
    System.out.println("plain? " + ContentTypeDetector.isHtml(plainTextContent)); // false
    System.out.println("html? " + ContentTypeDetector.isHtml(plainTextContent)); // false

/*    String[] urls = {
            "https://bigfile.mail.naver.com/download?fid=g/RcWrC516cXK3YwHquZKqE9KIYZKAU9KAKqKxulKx2dFIYwFqtrKAU9KxvlHqMqFqvlKztrMqU9KqMrpxt9KxvwFqiopAvmFAgqpzkv",
            "https://mail-bigfile.hiworks.biz/service/download/15d7d6dcd89ba8889753dfd1e3a137cdff74a74dde8e279e29aaf122a1a73760"
    };

    FileDownloadService downloader = new FileDownloadService();
    List<AttachVO> downloadedFiles = downloader.downloadFiles(urls);

    downloadedFiles.forEach(System.out::println);*/
  }
}
