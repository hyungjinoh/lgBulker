package com.rayful.lgbulker.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class True_Json_File_Maker_Test {

    @Test
    public void convertFlatJsonToJSONArray_ccc_to_ddd() throws IOException {
        File inputFile = new File("src/test/resources/2025-04-17_file.json");
        File outputFile = new File("src/test/resources/2025-04-17_file_true.json");

        StringBuilder jsonText = new StringBuilder();

        // 전체 파일 내용을 한 줄로 읽음
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonText.append(line.trim());
            }
        }

        // JSON 오브젝트들 분리
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

                    // ✅ 문자열 "null"을 실제 null 로 변환 (Mail_AbnormalNested 키에 한정)
                    Object abnormalValue = map.get("File_AbnormalNested");
                    if ("null".equals(abnormalValue)) {
                        map.put("File_AbnormalNested", null);
                    }

                    // ✅ File_GUID 정제
                    Object fileGuid = map.get("File_GUID");
                    if (fileGuid instanceof String) {
                        String sanitized = sanitizeFileGuidForPath((String) fileGuid);
                        map.put("File_GUID", sanitized);
                    }

                    // ✅ Mail_GUID 정제
                    Object mailGuid = map.get("Mail_GUID");
                    if (mailGuid instanceof String) {
                        String sanitized = sanitizeMailGuidForPath((String) mailGuid);
                        map.put("Mail_GUID", sanitized);
                    }

                    String filePath = "D:/1.data_poc/sampleJson/attach/";
                    // ✅ File_ContentPath 정제
                    Object filName = map.get("File_Name");
                    if (filName instanceof String) {
                        String File_ContentPath = filePath +  filName;
                        map.put("File_ContentPath", File_ContentPath);
                    }

                    resultList.add(map);
                    current.setLength(0); // 버퍼 초기화
                }
            }
        }

        // 결과 저장
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            mapper.writeValue(writer, resultList);
        }

        System.out.println("✅ ddd.json 저장 완료 → " + outputFile.getAbsolutePath());
    }

    public static String sanitizeFileGuidForPath(String mailGuid) {
        if (mailGuid == null) return null;

        return mailGuid
                .replaceAll("[\\\\/:*?\"<>|]", "_")   // 파일 금지 문자 → "_"
                .replaceAll("@", "_at_")              // 이메일 구분자 → _at_
                .replaceAll("T", "_")                 // ISO-8601 T → _
                .replaceAll("\\s+", "_");             // 공백 → "_"
    }

    public static String sanitizeMailGuidForPath(String mailGuid) {
        if (mailGuid == null) return null;

        return mailGuid
                .replaceAll("[\\\\/:*?\"<>|]", "_")   // 파일 금지 문자 → "_"
                .replaceAll("@", "_at_")              // 이메일 구분자 → _at_
                .replaceAll("T", "_")                 // ISO-8601 T → _
                .replaceAll("\\s+", "_");             // 공백 → "_"
    }
}
