package com.rayful.lgbulker.elm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rayful.lgbulker.service.EmailAttachmentProcessService;
import com.rayful.lgbulker.service.EmailAttachmentProcessor;
import com.rayful.lgbulker.util.BulkFileWriter;
import com.rayful.lgbulker.vo.LGFileMailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class EmlParserApp {
    private static final EmailAttachmentProcessor emailAttachmentProcessor = new EmailAttachmentProcessor();

    private static final BulkFileWriter bulkFileWriter = new BulkFileWriter();

    private static final String MERGED_DIR  = "D:/temp/merged";;

	private static final String START_FOLDER = "D:/temp/emails";  // EML 파일이 저장된 폴더
	private static final String DOWN_BASE_FOLDER = "D:/temp/attachments";  // 첨부파일 저장 BASE 폴더
    private static final String BULK_PATH = "D:/temp/bulkfiles";

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, "UTF-8"));  // ✅ 콘솔 UTF-8 출력 강제
        EmlParserApp app = new EmlParserApp();

        File folder = new File(START_FOLDER);

        File[] emlFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".eml"));
        if (emlFiles == null || emlFiles.length == 0) {
            System.out.println(".eml 파일이 없습니다.");
            return;
        }

        List<Map<String, Object>> emailDataList = new ArrayList<>();

        for (File emlFile : emlFiles) {
            System.out.println("\n@@대상: " + emlFile.getName());
            app.emlParsing(emailDataList, emlFile, DOWN_BASE_FOLDER);
        }
        
//        System.out.println(emailDataList);
        List<Map<String, Object>> finalMapList = emailAttachmentProcessor.processEmailAttachments(emailDataList);
        //벌크파일 생성호출
        bulkFileWriter.writeAsBulkJsonFiles(finalMapList, MERGED_DIR);
    }

    public void job_start() throws Exception {
        System.setOut(new PrintStream(System.out, true, "UTF-8"));  // ✅ 콘솔 UTF-8 출력 강제
        EmlParserApp app = new EmlParserApp();

        File folder = new File(START_FOLDER);

        File[] emlFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".eml"));
        if (emlFiles == null || emlFiles.length == 0) {
            System.out.println(".eml 파일이 없습니다.");
            return;
        }

        List<Map<String, Object>> emailDataList = new ArrayList<>();

        for (File emlFile : emlFiles) {
            System.out.println("\n@@대상: " + emlFile.getName());
            app.emlParsing(emailDataList, emlFile, DOWN_BASE_FOLDER);
        }

//        System.out.println(emailDataList);
        List<Map<String, Object>> finalMapList = emailAttachmentProcessor.processEmailAttachments(emailDataList);
        //벌크파일 생성호출
        bulkFileWriter.writeAsBulkJsonFiles(finalMapList, MERGED_DIR);
    }

    public void emlParsing(List<Map<String, Object>> emailDataList, File emlFile, String attachmentSaveDir) throws Exception {

        FileInputStream source = new FileInputStream(emlFile);
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage message = new MimeMessage(session, source);

        // 제목
        String subject = message.getSubject();
        if (subject != null) subject = MimeUtility.decodeText(subject);
        System.out.println("#제목: " + subject);

        // 작성자
        Address[] froms = message.getFrom();
        String sender = null;
        if (froms != null && froms.length > 0) {
            InternetAddress ia = (InternetAddress) froms[0];
            String personal = ia.getPersonal();
            String email = ia.getAddress();
            sender = (personal != null)
                    ? MimeUtility.decodeText(personal) + " <" + email + ">"
                    : MimeUtility.decodeText(ia.toString());
        }
        System.out.println("#작성자: " + sender);
        
        // 수신자
        String receiver = getRecipientsAsString(message, Message.RecipientType.TO);
        //printRecipients(message, Message.RecipientType.TO, "받는 사람(TO)");
        //printRecipients(message, Message.RecipientType.CC, "참조자(CC)");
        //printRecipients(message, Message.RecipientType.BCC, "숨은 참조(BCC)");
        System.out.println("#수신자: " + receiver);

        // 작성일
        String sendDtm = null;
        Date sentDate = message.getSentDate();
        if(sentDate != null) {
        	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        	sendDtm = sdf.format(sentDate);
        }
        System.out.println("#작성일: " + sendDtm);
        
        String messageId = message.getHeader("Message-ID", null);
        if (messageId == null || messageId.isEmpty()) {
            String rawKey = sentDate + sender + subject;
            messageId = DigestUtils.md5Hex(rawKey);
        }
        messageId = messageId.replaceAll("<", "").replaceAll(">", "");
        
        attachmentSaveDir = attachmentSaveDir + "/" + messageId;
        
        // 본문 & 일반첨부
        String bodyTotal = null;
        List<Map<String, Object>> gAttach = new ArrayList<>();
        
        Object content = message.getContent();
        
        if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            StringBuilder bodyFinal = new StringBuilder();

            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                String disposition = part.getDisposition();

                // 첨부파일
                if (disposition != null && disposition.equalsIgnoreCase(Part.ATTACHMENT)) {
                    String fileName = MimeUtility.decodeText(part.getFileName());
                    String safeFileName = UUID.randomUUID() + "_" + fileName;
                    System.out.println("#첨부파일: " + fileName);
                    saveAttachment(gAttach, part.getInputStream(), attachmentSaveDir, safeFileName, fileName);
                } else {
                    // 본문 텍스트 처리
                    if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
                        String charset = getCharset(part.getContentType());
                        InputStream is = part.getInputStream();
                        String body = new String(is.readAllBytes(), charset);

                        body = body.replaceAll("(?s)<!--\\s*수정부분\\s*:\\s*start\\s*-->.*?<!--\\s*수정부분\\s*:\\s*end\\s*-->", "");
                        if (part.isMimeType("text/html")) {
                            body = Jsoup.parse(body).text();
                        }
                        if (body != null && body.length() > 0) {
                            bodyFinal.append(cleanUpText(body));
                        }
                    } else {
                        StringBuilder bodyText = new StringBuilder();
                        extractBodyOnly(part, bodyText);
                        if (bodyText != null && bodyText.length() > 0) {
                            bodyFinal.append(cleanUpText(bodyText.toString()));
                        }
                    }
                }
            }
            System.out.println("#본문 : " + bodyFinal);
            bodyTotal = bodyFinal.toString();
        } else if (content instanceof String) {
            System.out.println("#본문(단일): " + content);
            bodyTotal = content.toString();
        }
        System.out.println("gAttach:" + gAttach);
        
        StringBuilder bodyText = new StringBuilder();
        Set<String> imageLinks = new HashSet<>();
        extractBody(message, bodyText, imageLinks);

        Document doc = Jsoup.parse(String.valueOf(bodyText));
        Elements linkElements = doc.select("a[href]");

        List<String> allLinks = linkElements.stream()
                .map(el -> el.attr("href").trim())
                .filter(link -> !link.isEmpty())
                .collect(Collectors.toList());

        List<String> downloadLinks = allLinks.stream()
                .filter(link -> !isImageLink(link))
                .collect(Collectors.toList());

        System.out.println("#본문 내 이미지 링크:");
        imageLinks.forEach(System.out::println);

        List<Map<String, Object>> iAttach = new ArrayList<>();
        
        List<BodyPart> inlineImageParts = new ArrayList<>();
        extractBodyAndCollectCids(message, bodyText, imageLinks, inlineImageParts);

        // 이미지 저장 처리
        for (String link : imageLinks) {
            if (link.startsWith("http")) {
                try {
                    downloadImageFromUrl(iAttach, link, attachmentSaveDir);
                } catch (IOException e) {
                    System.out.println("#외부 이미지 다운로드 실패: " + link);
                }
            } else if (link.startsWith("cid:")) {
                String cid = link.substring(4);
                saveCidImage(iAttach, cid, inlineImageParts, attachmentSaveDir);
            }
        }
        
        System.out.println("iAttach:" + iAttach);

        System.out.println("#대용량 다운로드 링크:");
        downloadLinks.forEach(System.out::println);
        
        List<Map<String, Object>> lAttach = new ArrayList<>();
        for(String url : downloadLinks) {
        	downloadFileFromTwoStepUrl_autoSession(lAttach, url, attachmentSaveDir);
        }
        
        System.out.println("lAttach:" + lAttach);
        
        // 1. 메일 본문 저장
        emailDataList.add(createEmail(messageId, subject, bodyTotal, sender, receiver, sendDtm));
        
        // 2. 일반 첨부파일 저장(이미지 포함)
        if(gAttach != null && gAttach.size() > 0) {
        	
        	for(int i = 0; i < gAttach.size(); i++) {
        		emailDataList.add(createEmailWithAttachment(messageId, subject, bodyTotal, sender, receiver, sendDtm, 
        				messageId+"_general_" + i, gAttach.get(i).get("filename").toString(), gAttach.get(i).get("filepath").toString(), "N"));
        	}
        }
        

        // 3. 이미지 저장(본문안에 있는 이미지)
        if(iAttach != null && iAttach.size() > 0) {
        	for(int i = 0; i < iAttach.size(); i++) {
        		emailDataList.add(createEmailWithAttachment(messageId, subject, bodyTotal, sender, receiver, sendDtm, 
        				messageId+"_bodyimage_" + i, iAttach.get(i).get("filename").toString(), iAttach.get(i).get("filepath").toString(), "N"));
        	}
        }
        
        // 4. 대용량파일
        if(lAttach != null && lAttach.size() > 0) {
        	for(int i = 0; i < lAttach.size(); i++) {
        		emailDataList.add(createEmailWithAttachment(messageId, subject, bodyTotal, sender, receiver, sendDtm, 
        				messageId+"_large_" + i, lAttach.get(i).get("filename").toString(), lAttach.get(i).get("filepath").toString(), "Y"));
        	}
        }
    }

    private boolean isImageLink(String url) {
        return url.matches("(?i).*\\.(jpg|jpeg|png|gif|bmp|svg|webp|ico)(\\?.*)?$");
    }

    private static String cleanUpText(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String getCharset(String contentType) {
        try {
            ContentType ct = new ContentType(contentType);
            String charset = ct.getParameter("charset");
            return charset != null ? charset : "UTF-8";
        } catch (Exception e) {
            return "UTF-8";
        }
    }

    private static void saveAttachment(List<Map<String, Object>> gAttach, InputStream input, String saveDir, String fileName, String realFileName) throws IOException {
        Files.createDirectories(Paths.get(saveDir));
        Path file = Paths.get(saveDir, fileName);

        try (OutputStream out = Files.newOutputStream(file)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = input.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
        
        Map<String, Object> tempMap =  new HashMap<String, Object>();
        tempMap.put("filename", realFileName);
        tempMap.put("filepath", file.toAbsolutePath());
        
        gAttach.add(tempMap);
        
        System.out.println("#첨부파일 저장 완료: " + file);
    }

//    private void printRecipients(MimeMessage message, Message.RecipientType type, String label) {
//        try {
//            String[] headers = message.getHeader(type.toString());
//            if (headers != null) {
//                for (String header : headers) {
//                    String[] addresses = header.split("[,;]");
//                    System.out.println("# " + label + ":");
//                    for (String addr : addresses) {
//                        addr = addr.trim();
//                        if (!addr.isEmpty()) {
//                            try {
//                                InternetAddress ia = new InternetAddress(addr);
//                                String personal = ia.getPersonal();
//                                String email = ia.getAddress();
//                                String decoded = (personal != null)
//                                        ? MimeUtility.decodeText(personal) + " <" + email + ">"
//                                        : MimeUtility.decodeText(ia.toString());
//                                System.out.println("   - " + decoded);
//                            } catch (Exception e) {
//                                System.out.println("   - [# 파싱 실패] " + addr);
//                            }
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            System.out.println("# " + label + " 추출 오류: " + e.getMessage());
//        }
//    }
    
    private static String getRecipientsAsString(MimeMessage message, Message.RecipientType type) {
//        try {
//            Address[] addresses = message.getRecipients(type);
//            if (addresses == null) return "";
//            return Arrays.stream(addresses)
//                    .map(addr -> {
//                        try {
//                            InternetAddress ia = (InternetAddress) addr;
//                            String personal = ia.getPersonal();
//                            String email = ia.getAddress();
//                            return (personal != null ? MimeUtility.decodeText(personal) + " <" + email + ">" : email);
//                        } catch (Exception e) {
//                            return addr.toString();
//                        }
//                    })
//                    .collect(Collectors.joining("; "));
//        } catch (MessagingException e) {
//            return "";
//        }
    	StringBuilder nameSb = new StringBuilder();
    	
    	try {
            String[] headers = message.getHeader(type.toString());
            if (headers != null) {
                for (String header : headers) {
                    String[] addresses = header.split("[,;]");
                    
                    
                    for (String addr : addresses) {
                        addr = addr.trim();
                        if (!addr.isEmpty()) {
                            try {
                                InternetAddress ia = new InternetAddress(addr);
                                String personal = ia.getPersonal();
                                String email = ia.getAddress();
                                String decoded = (personal != null)
                                        ? MimeUtility.decodeText(personal) + " <" + email + ">"
                                        : MimeUtility.decodeText(ia.toString());
                                if(nameSb != null && nameSb.length() > 0) {
                                	nameSb.append(",").append(decoded);
                                } else {
                                	nameSb.append(decoded);
                                }
                            } catch (Exception e) {
                                return "파싱 실패";
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        	return "오류발생";
        }
    	
    	return nameSb.toString();
    }

    private void extractBody(Part part, StringBuilder bodyText, Set<String> imageLinks) throws Exception {
        if (part.isMimeType("text/html") || part.isMimeType("text/plain")) {
            Object content = part.getContent();
            if (content instanceof String) {
                bodyText.append((String) content);
                extractImageCids((String) content, imageLinks);
            }
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                extractBody(multipart.getBodyPart(i), bodyText, imageLinks);
            }
        }
    }

    private void extractBodyOnly(Part part, StringBuilder bodyText) throws Exception {
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            if (content instanceof String) {
                bodyText.append((String) content);
            }
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                extractBodyOnly(multipart.getBodyPart(i), bodyText);
            }
        }
    }

    private void extractBodyAndCollectCids(Part part, StringBuilder bodyText, Set<String> imageLinks, List<BodyPart> inlineImageParts) throws Exception {
        if (part.isMimeType("text/html") || part.isMimeType("text/plain")) {
            Object content = part.getContent();
            if (content instanceof String) {
                String body = (String) content;
                bodyText.append(body);
                extractImageCids(body, imageLinks);
            }
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bp = multipart.getBodyPart(i);
                String[] contentIdHeader = bp.getHeader("Content-ID");
                if (contentIdHeader != null && contentIdHeader.length > 0) {
                    inlineImageParts.add(bp);
                }
                extractBodyAndCollectCids(bp, bodyText, imageLinks, inlineImageParts);
            }
        }
    }

    private void extractImageCids(String html, Set<String> cids) {
        Pattern pattern = Pattern.compile("(?i)<img[^>]+src=[\"']?(cid:[^\"'>\\s]+)[\"']?");
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            cids.add(matcher.group(1));
        }

        Pattern external = Pattern.compile("(?i)<img[^>]+src=[\"']?(https?://[^\"'>\\s]+)[\"']?");
        Matcher matcher2 = external.matcher(html);
        while (matcher2.find()) {
            cids.add(matcher2.group(1));
        }
    }

    private void downloadImageFromUrl(List<Map<String, Object>> iAttach, String imageUrl, String saveDir) throws IOException {
        URL url = new URL(imageUrl);
        InputStream in = url.openStream();
        String fileName = UUID.randomUUID() + "_" + Paths.get(url.getPath()).getFileName().toString();
        Path outputPath = Paths.get(saveDir, fileName);
        Files.createDirectories(Paths.get(saveDir));
        Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
        in.close();
        
        Map<String, Object> tempMap =  new HashMap<String, Object>();
        tempMap.put("filename", fileName);
        tempMap.put("filepath", outputPath.toAbsolutePath());
        
        iAttach.add(tempMap);
        System.out.println("# 외부 이미지 저장됨: " + outputPath);
    }

    private void saveCidImage(List<Map<String, Object>> iAttach, String cid, List<BodyPart> parts, String saveDir) {
        for (BodyPart part : parts) {
            try {
                String[] headers = part.getHeader("Content-ID");
                if (headers != null && headers.length > 0) {
                    String rawCid = headers[0].replaceAll("[<>]", "").trim();
                    if (cid.equals(rawCid)) {
                        String contentType = part.getContentType();
                        String extension = contentType.split("/")[1].split(";")[0];
                        String fileName = UUID.randomUUID() + "_cid." + extension;

                        InputStream is = part.getInputStream();
                        saveAttachment(iAttach, is, saveDir, fileName, fileName);
                        System.out.println("# CID 이미지 저장됨: " + fileName);
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("# CID 이미지 저장 실패 (" + cid + "): " + e.getMessage());
            }
        }
        System.out.println("# CID 이미지 파트 없음: " + cid);
    }

    // URL 추출 유틸리티 내부 클래스
    public static class BodyParserService {
        public Set<String> extractUrls_normallink(String text, boolean onlyImages) {
            Set<String> urls = new LinkedHashSet<>();
            String urlRegex = "(https?://[^\\s\"'<>]+)";
            Pattern pattern = Pattern.compile(urlRegex);
            Matcher matcher = pattern.matcher(text);

            while (matcher.find()) {
                urls.add(matcher.group(1));
            }
            return urls;
        }
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
    
    private static void downloadFileFromTwoStepUrl_autoSession(List<Map<String, Object>> lAttach, String firstUrl, String attachmentSaveDir) throws Exception {
        //String firstUrl = "https://bigattach.lg.co.kr:15000/storage/1C978EDC1584BF3249258C65001E4CF9/download.do";

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
       // Element realLink = doc.selectFirst("a[href*=download.do]");
        
        
        // 다운로드 파일 리스트 뽑기
        Elements realLink = doc.select("a[title][href*=download.do]");
        if (realLink == null) {
            throw new RuntimeException("실제 다운로드 링크를 HTML에서 찾지 못했습니다.");
        }
        for (Element link : realLink) {
            String realFileName = link.attr("title").trim(); // 예: Zimperium_사용자_매뉴얼_ver0.1.pptx
            
            System.out.println("# realFileName: " + realFileName);

            String realUrl = realLink.attr("href");
            if (!realUrl.startsWith("http")) {
                URI base = URI.create(firstUrl);
                realUrl = base.resolve(realUrl).toString();
            }

            System.out.println("# JSESSIONID: " + jsessionId);
            System.out.println("# 실제 다운로드 링크: " + realUrl);

            // Step 3: 진짜 다운로드 요청
            HttpRequest realRequest = HttpRequest.newBuilder()
                    .uri(URI.create(realUrl))
                    .GET()
                    .header("User-Agent", userAgent())
                    .header("Referer", firstUrl)
                    .header("Cookie", "JSESSIONID=" + jsessionId)
                    .build();

            HttpResponse<InputStream> realResponse = client.send(realRequest, HttpResponse.BodyHandlers.ofInputStream());

            // 파일명 추출 (없으면 기본값)
//            String fileName = extractFileNameFromHeader(realResponse, "downloaded_file.pptx");
//            fileName = sanitizeFileName(fileName);
            String uuidFileName = UUID.randomUUID() + "_" + realFileName;
            Path outputPath = Paths.get(attachmentSaveDir, uuidFileName);  // 절대경로로 저장
            Files.createDirectories(outputPath.getParent()); // 부모 디렉토리 생성

            try (InputStream in = realResponse.body();
                 FileOutputStream out = new FileOutputStream(outputPath.toFile())) {

                byte[] buffer = new byte[8192];
                int totalBytes = 0;
                int len;

                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    totalBytes += len;
                }

                System.out.println("# 다운로드 완료 (" + totalBytes + " bytes): " + outputPath.toAbsolutePath());
            }

            Map<String, Object> tempMap =  new HashMap<String, Object>();
            tempMap.put("filename", realFileName);
            tempMap.put("filepath", outputPath.toAbsolutePath());
            
            lAttach.add(tempMap);
        }
    }

    private static String userAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    }


    // createBulkFiles(), doIndexing() 등은 동일 방식으로 별도 메서드로 유지 가능

    public void createBulkFiles() throws IOException {
        File[] files = new File(MERGED_DIR).listFiles((dir, name) -> name.contains("attach_"));
        if (files == null || files.length == 0) {
            log.warn("처리할 JSON 파일이 없습니다.");
            return;
        }

        Files.createDirectories(Path.of(BULK_PATH));

        for (File file : files) {
            createBulkFileFromAttachJson(file);
        }

//        bulkerLogVO.attach.setTotalCount(Total_AttachCount);
//    log.info("=============처리대상 총 첨부파일 갯수 : {}", bulkerLogVO.attach.getTotalCount());
    }


    private void createBulkFileFromAttachJson(File file) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            List<LGFileMailVO> attachList = objectMapper.readValue(file, new TypeReference<>() {
            });
            File bulkFile = new File(BULK_PATH, "bulk_" + file.getName());

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(bulkFile))) {
                for (LGFileMailVO attach : attachList) {
                    String key = attach.getKey();
                    String meta = String.format("{\"index\": {\"_index\": \"idx_email\", \"_id\": \"%s\"}}", key);
                    writer.write(meta);
                    writer.newLine();
                    writer.write(objectMapper.writeValueAsString(objectMapper.valueToTree(attach)));
                    writer.newLine();
                }
            }
            log.info("{} >> {} 변환 완료", file.getName(), bulkFile.getName());

            ///////////////////
            long attachCount = attachList.stream().map(LGFileMailVO::getAttach_id)     // attach_id 추출
                    .filter(Objects::nonNull)        // null 값 방지
                    .distinct()                      // 고유값으로 필터링
                    .count()
                    ;                        // 개수 세기

//      log.info("Uniq attach_id 개수: {}", attachCount);
            Set<String> uniqueAttachIds = attachList
                    .stream()
                    .map(LGFileMailVO::getAttach_id)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet())
                    ;

//      log.info("Uniq attach_id 목록: {}", uniqueAttachIds);
            List<String> allList = uniqueAttachIds.stream().filter(id -> id != null && !id.isBlank())  // null 또는 공백 문자열 제외
                    .collect(Collectors.toList())
                    ;

//            totalAttachIds.addAll(allList);         // 고유 attach_id를 누적시킴 :통계

            ////////////////////////
        } catch (IOException e) {
            log.error("bulk 파일 처리 중 오류 발생: {}", file.getName(), e);
        }
    }


    //한글 파일명 깨지지 않고 정상 다운로드 됨.
//    private static String extractFileNameFromHeader(HttpResponse<?> response, String fallback) {
//        return response.headers()
//                .firstValue("Content-Disposition")
//                .map(header -> {
//                    try {
//                        // 1. filename*=UTF-8''... 처리
//                        Matcher utf8Matcher = Pattern.compile("filename\\*=UTF-8''(.+?)(?:;|$)").matcher(header);
//                        if (utf8Matcher.find()) {
//                            String encoded = utf8Matcher.group(1);
//                            return java.net.URLDecoder.decode(encoded, "UTF-8");
//                        }
//
//                        // 2. filename="..." 직접 추출 (MIME decode 안함!)
//                        Matcher matcher = Pattern.compile("filename=\"?([^\";]+)\"?").matcher(header);
//                        if (matcher.find()) {
//                            String rawName = matcher.group(1);
//
//                            // ✅ 2-1. Content-Disposition 이 그냥 UTF-8 (깨진 상태로 들어왔을 때) 가정하고 ISO-8859-1 → UTF-8 변환
//                            return new String(rawName.getBytes("ISO-8859-1"), "UTF-8");
//                        }
//
//                    } catch (Exception e) {
//                        System.out.println("# 파일명 디코딩 실패: " + e.getMessage());
//                    }
//                    return fallback;
//                })
//                .orElse(fallback);
//    }
    
}
