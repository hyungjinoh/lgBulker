package com.rayful.lgbulker.service;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.*;

import java.nio.charset.Charset;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class EmailAttachmentProcessor {
    
    private static final String EXE_PATH;
    private static final String BASE_OUTPUT_PATH;
    private static final String IMAGE_OUTPUT_PATH;
    private static final String TEXT_OUTPUT_PATH;

    static {
        boolean isWindows = isWindows();
//        EXE_PATH = isWindows ? "D:/synap/v4/snf_exe.exe" : "/data/synap/v4/snf_exe";
        EXE_PATH = isWindows ? "D:/1.data_poc/synap_v4/snf_exe.exe" : "/data/synap/v4/snf_exe";

        BASE_OUTPUT_PATH = isWindows ? "D:/temp/synap" : "/data/temp/synap";
        IMAGE_OUTPUT_PATH = BASE_OUTPUT_PATH + "/image";
        TEXT_OUTPUT_PATH = BASE_OUTPUT_PATH + "/text";
    }
    
    private static final String OCR_API_URL = "https://ocr.edentns.ai/document/general"; 


    public static void main(String[] args) {
        // 샘플 이메일 데이터 생성
        List<Map<String, Object>> emailDataList = generateSampleData();
        
        // 이메일 및 첨부파일 처리
        processEmailAttachments(emailDataList);
    }

    /**
     * 샘플 데이터를 생성하는 메서드
     */
    private static List<Map<String, Object>> generateSampleData() {
        List<Map<String, Object>> emailDataList = new ArrayList<>();

        if(isWindows()) {
	        emailDataList.add(createEmail("MAIL_001", "Test Email 001", "상호야~ 잘좀하자~~제발~~", "samuel@rayful.com", "ssh6019@rayful.com", "2025-03-24T13:59:53.334"));
	        emailDataList.add(createEmail("MAIL_002", "Test Email 002", "민기야~ 잘좀하자~~제발~~", "samuel@rayful.com", "mkk232@rayful.com", "2025-03-24T13:59:54.234"));
	        emailDataList.add(createEmailWithAttachment("MAIL_001", "Test Email 001", "상호야~ 잘좀하자~~제발~~", "samuel@rayful.com", "ssh6019@rayful.com", "2025-03-24T13:59:54.234", "ATTACH_001", "1.jpg", "D:/temp/input/1.jpg", "N"));
	        emailDataList.add(createEmailWithAttachment("MAIL_002", "Test Email 002", "민기야~ 잘좀하자~~제발~~", "samuel@rayful.com", "mkk232@rayful.com", "2025-03-24T13:59:54.234", "ATTACH_002", "EDM 관리자 사용자 매뉴얼.docx", "D:/temp/input/EDM 관리자 사용자 매뉴얼.docx", "Y"));
        } else {
        	emailDataList.add(createEmail("MAIL_001", "Test Email 001", "상호야~ 잘좀하자~~제발~~", "samuel@rayful.com", "ssh6019@rayful.com", "2025-03-24T13:59:53.334"));
	        emailDataList.add(createEmail("MAIL_002", "Test Email 002", "민기야~ 잘좀하자~~제발~~", "samuel@rayful.com", "mkk232@rayful.com", "2025-03-24T13:59:54.234"));
	        emailDataList.add(createEmailWithAttachment("MAIL_001", "Test Email 001", "상호야~ 잘좀하자~~제발~~", "samuel@rayful.com", "ssh6019@rayful.com", "2025-03-24T13:59:54.234", "ATTACH_001", "로그인.hwpx", "/data/temp/input/로그인.hwpx", "N"));
	        emailDataList.add(createEmailWithAttachment("MAIL_002", "Test Email 002", "민기야~ 잘좀하자~~제발~~", "samuel@rayful.com", "mkk232@rayful.com", "2025-03-24T13:59:54.234", "ATTACH_002", "EDM 관리자 사용자 매뉴얼.docx", "/data/temp/input/EDM 관리자 사용자 매뉴얼.docx", "Y"));
        }
        return emailDataList;
    }

    /**
     * 일반 이메일 샘플 데이터 생성
     */
    private static Map<String, Object> createEmail(String emId, String subject, String body, String sender, String receiver, String sendDtm) {
        Map<String, Object> email = new HashMap<>();
        email.put("em_id", emId);
        email.put("subject", subject);
        email.put("em_body", body);
        email.put("sender", sender);
        email.put("receiver", receiver);
        email.put("senddtm", sendDtm);
        return email;
    }

    /**
     * 첨부파일이 있는 이메일 샘플 데이터 생성
     */
    private static Map<String, Object> createEmailWithAttachment(String emId, String subject, String body, String sender, String receiver, String sendDtm, String attachId, String attachName, String attachPath, String linkYn) {
        Map<String, Object> email = new HashMap<>();
        email.put("em_id", emId);
        email.put("subject", subject);
        email.put("em_body", body);
        email.put("sender", sender);
        email.put("receiver", receiver);
        email.put("senddtm", sendDtm);
        email.put("attach_id", attachId);
        email.put("attach_name", attachName);
        email.put("attach_path", attachPath);
        email.put("link_yn", linkYn);
        return email;
    }

    /**
     * 이메일 및 첨부파일 처리 로직
     */
    public static void processEmailAttachments(List<Map<String, Object>> emailDataList) {
        
    	for (Map<String, Object> emailData : emailDataList) {
            String emId = (String) emailData.get("em_id");
            String attachId = (String) emailData.get("attach_id");
            String attachName = (String) emailData.get("attach_name");
            String attachPath = (String) emailData.get("attach_path");
            
            if (attachId != null) {
            	if (isImageExtractableDocument(attachName)) { // 이미지를 추출해 작업할 파일
                    extractFile2TextWithOcr(emailData, attachPath, emId, attachId);
                    
                    System.out.println("본문추출1: " + emailData.get("attach_body"));
                } else if (isImageFile(attachName)) { // 이미지 파일
                    extractImage2Text(emailData, attachPath, emId, attachId);
                    
                    System.out.println("본문추출2: " + emailData.get("attach_body"));
                } else { // 그외 대상
                	extractOtherFile2Text(emailData, attachPath, emId, attachId);
                	
                	System.out.println("본문추출3: " + emailData.get("attach_body"));
                }
            } 
        }
    }
    
    /**
     * 문서 내 이미지를 추출후 OCR 호출하여 제공받은 텍스트를 문서내용과 Merge 하여 본문저장
     */
    private static void extractFile2TextWithOcr(Map<String, Object> emailData, String filePath, String emId, String attachId) {
        System.out.println("call extractFile2TextWithOcr(): " + filePath);
        
        try {
            // 출력 디렉토리 생성
            File outputDir = new File(IMAGE_OUTPUT_PATH + "/" + emId + "_" + attachId);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            File contentFile = new File(outputDir, "content.txt");
            
            ProcessBuilder processBuilder = new ProcessBuilder(EXE_PATH, "-ei", IMAGE_OUTPUT_PATH + "/" + emId + "_" + attachId , filePath);
            processBuilder.redirectErrorStream(true); // 에러 출력도 통합
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("MS949"))); 
                BufferedWriter writer = new BufferedWriter(new FileWriter(contentFile))) {
               String line;
               while ((line = reader.readLine()) != null) {
                   writer.write(line);
                   writer.newLine();
               }
            }

            int exitCode = process.waitFor();
            System.out.println("추출 완료: " + filePath + " (exitCode: " + exitCode + ")");
            
            // 본문에서 이미지 OCR 처리
            String updatedBody = replaceImageIndexesWithOCR(readFileContent(contentFile), emId, attachId);
            
            // OCR 처리된 내용 다시 파일에 저장
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(contentFile))) {
                writer.write(updatedBody);
            }
            
            // attach_body 필드에 전체 추출내용 put
            emailData.put("attach_body", updatedBody);
        } catch (Exception e) {
            System.err.println("문서 추출 중 오류 발생: " + e.getMessage());
        }
    }
    
    /**
     * 이미지파일을 OCR API를 호출하여 제공받은 텍스트를 본문저장
     */
    private static void extractImage2Text(Map<String, Object> emailData, String filePath, String emId, String attachId) {
    	System.out.println("call extractImage2Text(): " + filePath);
        
        // attach_body 필드에 전체 추출내용 put
        emailData.put("attach_body", callOCRApi(filePath));
    }

    /**
     * 기타 문서내용을 본문저장
     */
    private static void extractOtherFile2Text(Map<String, Object> emailData, String filePath, String emId, String attachId) {
    	System.out.println("call extractOtherFile2Text(): " + filePath);
        
        try {
            // 출력 디렉토리 생성
            File outputDir = new File(TEXT_OUTPUT_PATH + "/" + emId + "_" + attachId);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            File contentFile = new File(outputDir, "content.txt");
            
            ProcessBuilder processBuilder = new ProcessBuilder(EXE_PATH, filePath);
            processBuilder.redirectErrorStream(true); // 에러 출력도 통합
            Process process = processBuilder.start();

            Charset encoding = isWindows() ? Charset.forName("MS949") : StandardCharsets.UTF_8;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), encoding)); 
            	     BufferedWriter writer = new BufferedWriter(new FileWriter(contentFile))) {
               String line;
               while ((line = reader.readLine()) != null) {
                   writer.write(line);
                   writer.newLine();
               }
            }

            int exitCode = process.waitFor();
            System.out.println("추출 완료: " + filePath + " (exitCode: " + exitCode + ")");
            
            // 본문 추출 내용 출력
            emailData.put("attach_body", readFileContent(contentFile));
        } catch (Exception e) {
            System.err.println("문서 추출 중 오류 발생: " + e.getMessage());
        }
    }

    /**
     * 문서추출 내용중 특정 문자열 패턴을 이미지에서 추출한 텍스트와 replace 한다. 
     */
    private static String replaceImageIndexesWithOCR(String text, String emId, String attachId) {
        Pattern pattern = Pattern.compile("\\{\\.\\.IMAGE_INDEX:(\\d+)\\}");
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String imageIndex = matcher.group(1);
            String imagePath = IMAGE_OUTPUT_PATH + "/" + emId + "_" + attachId + "/img" + imageIndex + ".png";
            String ocrText = callOCRApi(imagePath);
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(ocrText));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 문서내용이 저장된 txt 파일을 read 한다.
     */
    private static String readFileContent(File file) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            System.err.println("파일 읽기 오류: " + e.getMessage());
        }
        return content.toString();
    }

    /**
     * OCR API를 호출하여 이미지에서 추출한 텍스트를 반환한다.
     */
    private static String callOCRApi(String imagePath) {
        System.out.println("OCR API 호출: " + imagePath);

        try {
            File file = new File(imagePath);
            byte[] fileContent = Files.readAllBytes(file.toPath());
            String base64Encoded = Base64.getEncoder().encodeToString(fileContent);

            // JSON 요청 문자열을 빌더 형식으로 생성
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{")
                .append("\"version\":\"V1\",")
                .append("\"requestId\":\"").append(UUID.randomUUID()).append("\",")
                .append("\"timestamp\":").append(System.currentTimeMillis()).append(",")
                .append("\"images\":[{")
                    .append("\"format\":\"").append(getFileExtension(file.getName())).append("\",")
                    .append("\"data\":\"").append(base64Encoded).append("\",")
                    .append("\"name\":\"").append(file.getName()).append("\",")
                    .append("\"option\":{\"pageRange\":[0]}")
                .append("}],")
                .append("\"details\":\"text\"")
                .append("}");

            URL url = new URL(OCR_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBuilder.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input);
            }

            // 응답 처리
            if (connection.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return "[[IMAGE TEXT : " + parseOcrTextFromResponse(response.toString()) + "]]";
                }
            } else {
                System.err.println("OCR API 호출 실패 (HTTP " + connection.getResponseCode() + ")");
                return "[OCR_ERROR]";
            }

        } catch (Exception e) {
            System.err.println("OCR API 요청 중 오류 발생: " + e.getMessage());
            return "[OCR_ERROR]";
        }
    }

    /**
     *  ocr api 호출 시 리턴 받은 response를 파싱하여 문자열로 리턴
     */
    private static String parseOcrTextFromResponse(String jsonResponse) {
        StringBuilder extractedText = new StringBuilder();
        try {
            JsonObject obj = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray result = obj.getAsJsonArray("result");

            for (JsonElement pageElem : result) {
                JsonObject page = pageElem.getAsJsonObject();
                JsonArray lines = page.getAsJsonArray("lines");

                for (JsonElement lineElem : lines) {
                    JsonObject line = lineElem.getAsJsonObject();
                    JsonArray words = line.getAsJsonArray("words");

                    for (JsonElement wordElem : words) {
                        JsonObject word = wordElem.getAsJsonObject();
                        extractedText.append(word.get("text").getAsString()).append(" ");
                    }
                    extractedText.append("\n");
                }
            }
        } catch (Exception e) {
            System.err.println("OCR 응답 파싱 오류: " + e.getMessage());
            return "[OCR_PARSE_ERROR]";
        }
        return extractedText.toString().trim();
    }

    /**
     * 파일 확장자 추출
     */
    private static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 이미지 추출 대상 문서 파일인지 확인하는 메서드
     */
    private static boolean isImageExtractableDocument(String fileName) {
    	return fileName.toLowerCase().matches(".*\\.(hwp|hwpx|doc|docx|ppt|pptx|xls|xlsx|pdf)$");
    }

    /**
     * 이미지 파일인지 확인하는 메서드
     */
    private static boolean isImageFile(String fileName) {
    	return fileName.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|tiff|bmp)$");
    }
    
    /**
     * 윈도우 여부 확인하는 메서드
     */
  	private static boolean isWindows() {
  	    return System.getProperty("os.name").toLowerCase().contains("win");
  	}
}