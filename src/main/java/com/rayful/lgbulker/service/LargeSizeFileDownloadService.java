package com.rayful.lgbulker.service;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LargeSizeFileDownloadService {

    @Value("${app.paths.input.download}")
    private String DOWNLOAD_DIR;

    // 네이버 대용량 파일전송 링크...
    public String downloadFileFromTwoStepUrl_autoSession(String firstUrl) throws Exception {
//        String firstUrl = "https://bigattach.lg.co.kr:15000/storage/1C978EDC1584BF3249258C65001E4CF9/download.do";

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        // Step 1: 첫 URL 접속 → HTML + Set-Cookie 헤더 받기
        HttpRequest firstRequest = HttpRequest.newBuilder()
                .uri(URI.create(firstUrl))
                .GET()
                .header("User-Agent", userAgent())
                .build();

        HttpResponse<String> firstResponse = client.send(firstRequest, HttpResponse.BodyHandlers.ofString());

        // 세션 쿠키 추출
        String jsessionId = firstResponse.headers()
                .allValues("set-cookie").stream()
                .filter(h -> h.contains("JSESSIONID"))
                .findFirst()
                .map(cookie -> cookie.split(";")[0].split("=")[1])
                .orElseThrow(() -> new RuntimeException("JSESSIONID 쿠키가 없습니다."));

        // Step 2: HTML 본문에서 실제 다운로드 링크 추출
        Document doc = Jsoup.parse(firstResponse.body());
        Element realLink = doc.selectFirst("a[href*=download.do]");
        if (realLink == null) {
            throw new RuntimeException("실제 다운로드 링크를 HTML에서 찾지 못했습니다.");
        }

        String realUrl = realLink.attr("href");
        if (!realUrl.startsWith("http")) {
            URI base = URI.create(firstUrl);
            realUrl = base.resolve(realUrl).toString();
        }

        System.out.println("✅ JSESSIONID: " + jsessionId);
        System.out.println("✅ 실제 다운로드 링크: " + realUrl);

        // Step 3: 진짜 다운로드 요청
        HttpRequest realRequest = HttpRequest.newBuilder()
                .uri(URI.create(realUrl))
                .GET()
                .header("User-Agent", userAgent())
                .header("Referer", firstUrl)
                .header("Cookie", "JSESSIONID=" + jsessionId)
                .build();

        HttpResponse<InputStream> realResponse = client.send(realRequest, HttpResponse.BodyHandlers.ofInputStream());

        // 파일명 추출 (없으면 기본값), 한글깨지지 않도록 처리
        String fileName = extractFileNameFromHeader(realResponse, "downloaded_file.pptx");

        Path outputPath = Paths.get(DOWNLOAD_DIR, fileName);
        Files.createDirectories(outputPath.getParent());

        try (InputStream in = realResponse.body();
             FileOutputStream out = new FileOutputStream(outputPath.toFile())) {

            byte[] buffer = new byte[8192];
            int totalBytes = 0;
            int len;

            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                totalBytes += len;
            }

            System.out.println("✅ 다운로드 완료 (" + totalBytes + " bytes): " + outputPath.toAbsolutePath());

            return outputPath.toAbsolutePath().toString();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return "";
        }
    }

    private String userAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    }


    //한글 파일명 깨지지 않고 정상 다운로드 됨.
    private String extractFileNameFromHeader(HttpResponse<?> response, String fallback) {
        return response.headers()
                .firstValue("Content-Disposition")
                .map(header -> {
                    try {
                        // 1. filename*=UTF-8''... 처리
                        Matcher utf8Matcher = Pattern.compile("filename\\*=UTF-8''(.+?)(?:;|$)").matcher(header);
                        if (utf8Matcher.find()) {
                            String encoded = utf8Matcher.group(1);
                            return java.net.URLDecoder.decode(encoded, "UTF-8");
                        }

                        // 2. filename="..." 직접 추출 (MIME decode 안함!)
                        Matcher matcher = Pattern.compile("filename=\"?([^\";]+)\"?").matcher(header);
                        if (matcher.find()) {
                            String rawName = matcher.group(1);

                            // ✅ 2-1. Content-Disposition 이 그냥 UTF-8 (깨진 상태로 들어왔을 때) 가정하고 ISO-8859-1 → UTF-8 변환
                            return new String(rawName.getBytes("ISO-8859-1"), "UTF-8");
                        }

                    } catch (Exception e) {
                        System.out.println("⚠️ 파일명 디코딩 실패: " + e.getMessage());
                    }
                    return fallback;
                })
                .orElse(fallback);
    }

}
