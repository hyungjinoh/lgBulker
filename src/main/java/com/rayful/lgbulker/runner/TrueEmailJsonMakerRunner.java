package com.rayful.lgbulker.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Slf4j
public class TrueEmailJsonMakerRunner {

    public void run(ApplicationArguments args) {
        try {
            String inputDirPath = args.getOptionValues("input").get(0);
            Path inputDir = Paths.get(inputDirPath);

            if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
                log.error("❌ 입력 경로가 존재하지 않거나 디렉토리가 아닙니다: {}", inputDirPath);
                return;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "*.json")) {
                for (Path inputFile : stream) {
                    processSingleJsonFile(inputFile.toFile());
                }
            }

        } catch (Exception e) {
            log.error("⚠️ 이메일 JSON 처리 중 오류 발생", e);
        }
    }
    private void processSingleJsonFile(File inputFile) {
        try {
            String baseName = inputFile.getName().replaceAll("\\.json$", "");
            File outputFile = new File(inputFile.getParent(), baseName + "_true.json");

            log.info("📂 처리 중: {}", inputFile.getName());
            log.info("📄 결과 저장: {}", outputFile.getName());

            StringBuilder jsonText = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonText.append(line.trim());
                }
            }

            List<Map<String, Object>> resultList = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();

            int depth = 0;
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < jsonText.length(); i++) {
                char ch = jsonText.charAt(i);
                if (ch == '{') depth++;
                if (depth > 0) current.append(ch);
                if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        String oneJson = current.toString();
                        Map<String, Object> map = mapper.readValue(oneJson, Map.class);

                        if ("null".equals(map.get("Mail_AbnormalNested"))) {
                            map.put("Mail_AbnormalNested", null);
                        }

                        Object mailGuid = map.get("Mail_GUID");
                        if (mailGuid instanceof String) {
                            map.put("Mail_GUID", sanitizeMailGuidForPath((String) mailGuid));
                        }

                        resultList.add(map);
                        current.setLength(0);
                    }
                }
            }

            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
                mapper.writeValue(writer, resultList);
            }

            log.info("✅ 변환 완료: {}", outputFile.getName());

        } catch (Exception e) {
            log.error("❌ {} 처리 중 오류 발생", inputFile.getName(), e);
        }
    }

    private String sanitizeMailGuidForPath(String mailGuid) {
        if (mailGuid == null) return null;
        return mailGuid
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("@", "_at_")
                .replaceAll("T", "_")
                .replaceAll("\\s+", "_");
    }
}
