package com.rayful.lgbulker.util;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ImageUrlExtractor {

    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp");

//    public static Set<String> extractImageUrls(String content, boolean isHtml) {
//        // 모든 URL 추출
//        Set<String> allUrls = extractUrls(content, isHtml);
//
//        // 이미지 URL만 필터링
//        return allUrls.stream()
//                .filter(ImageUrlExtractor::isImageUrl)
//                .collect(Collectors.toSet());
//    }

    public static List<String> extractImageUrls(String content, boolean isHtml) {
        // 모든 URL 추출
        Set<String> allUrls = extractUrls(content, isHtml);

        // 이미지 URL만 필터링해서 리스트로 반환
        return allUrls.stream()
                .filter(ImageUrlExtractor::isImageUrl)
                .collect(Collectors.toList());
    }


    // URL이 이미지 확장자로 끝나는지 확인
    static boolean isImageUrl(String url) {
        String lowerUrl = url.toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(lowerUrl::endsWith);
    }

    // 기존 extractUrls 메서드 사용
    public static Set<String> extractUrls(String content, boolean isHtml) {
        if (isHtml) {
            return UrlExtractor.extractUrlsFromHtml(content);
        } else {
            return UrlExtractor.extractUrlsFromPlainText(content);
        }
    }
}

