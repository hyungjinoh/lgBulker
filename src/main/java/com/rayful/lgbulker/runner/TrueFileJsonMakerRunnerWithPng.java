package com.rayful.lgbulker.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Slf4j
public class TrueFileJsonMakerRunnerWithPng  {

    public void run(ApplicationArguments args) {
        try {
            List<String> inputValues = args.getOptionValues("input");
            if (inputValues == null || inputValues.isEmpty()) {
                log.error("No input file specified.  --input=dir_path를 포함시키세요");
                return;
            }

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
            log.error("⚠️ 전체 파일 처리 중 오류 발생", e);
        }
    }

    private void processSingleJsonFile(File inputFile) {
        try {
            String baseName = inputFile.getName().replaceAll("\\.json$", "");
            File outputFile = new File(inputFile.getParent()+File.separator+"png", baseName + "_true.json");

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
            List<Map<String, Object>> resultListpng = new ArrayList<>();
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

                        if ("null".equals(map.get("File_AbnormalNested"))) {
                            map.put("File_AbnormalNested", null);
                        }

                        Object fileGuid = map.get("File_GUID");
                        if (fileGuid instanceof String) {
                            map.put("File_GUID", sanitizeGuid((String) fileGuid));
                        }

                        Object mailGuid = map.get("Mail_GUID");
                        if (mailGuid instanceof String) {
                            map.put("Mail_GUID", sanitizeGuid((String) mailGuid));
                        }

                        Object fileName = map.get("File_Name");
                        if (fileName instanceof String) {
                            map.put("File_Name", fileName.toString());
                        }

                        String filepath = "";
                        Object File_ContentPath = map.get("File_ContentPath");
                        if (File_ContentPath instanceof String) {
//                            String filePath = "D:/1.data_poc/sampleJson/attach/" + fileName;
                            String Str_File_ContentPath = "/data/poc" + File_ContentPath;
                            map.put("File_ContentPath", Str_File_ContentPath);
                            map.put("attach_path", Str_File_ContentPath);

                            filepath = Str_File_ContentPath;
                        }

                        //파일명이 png인것만 별도저장.
                        if(filepath.toLowerCase().contains(".png")) {
                            resultList.add(map);
                        }

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

    private String sanitizeGuid(String guid) {
        return guid
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("@", "_at_")
                .replaceAll("T", "_")
                .replaceAll("\\s+", "_");
    }
}
