package com.rayful.lgbulker.util;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.rayful.lgbulker.util.ImageUrlExtractor.extractUrls;
import static org.junit.jupiter.api.Assertions.*;

class ImageUrlExtractorTest {

    @Test
    void extractImageUrls() {

        String body = "이미지 링크 첨부 1개    \"이미지 링크 첨부 1개    https://postfiles.pstatic.net/MjAyNTAzMjFfMjUg/MDAxNzQyNTQ2NDQ2Njcy.pgCdBr_zLiEio2MkPXGuPoykjg7hIm4CRRV18GdEhKIg.xqp0GFk4aCAnUMFi_QGIMj4_SpBm4GKa5o-IKqBKBSQg.JPEG/%EB%B0%98%EB%93%B1-001.jpg  <br><br> https://office.hiworks.com/rayful.com/common/logo    블로그서명 jinhoh21님의 블로그 안녕하세요.\"";

        Set<String> allUrls = extractUrls(body, false);

        // 이미지 URL만 필터링해서 리스트로 반환
        System.out.println(
                allUrls.stream()
                .filter(ImageUrlExtractor::isImageUrl)
                .collect(Collectors.toList())
        );

    }
}