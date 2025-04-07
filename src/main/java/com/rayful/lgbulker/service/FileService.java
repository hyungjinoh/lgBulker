package com.rayful.lgbulker.service;

import com.rayful.lgbulker.module.EmailMeta;
import com.rayful.lgbulker.vo.LGFileMailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

  @Value("${app.paths.attachments}")
  private String ATTACHMENTS_PATH;     //라. 첨부파일저장경로1

  @Value("${app.paths.synap_exe}") private String SYNAP_EXE;


  //파일경로 확인
  private File resolveAttachFile(String attachName) {
    Path path = Paths.get(attachName);
    return path.isAbsolute() ? path.toFile() : new File(ATTACHMENTS_PATH, attachName);
  }


  // 파일에서 첨부내용 추출
  private String extractAttachBody(File file) {
    File tempFile = new File(file.getParent(), getFileNameOnly(file.getName()) + ".txt");
    String raw = convertWithExternalProgram(file.toString(), tempFile.toString());

    if(tempFile.exists()) { // 필터링처리후, txt 파일 삭제
      tempFile.delete();
    }


    return cleanText(raw);
  }

  // 사이냅필터 사용하여 텍스트 추출후 정제.
  private String cleanText(String rawText) {
    return rawText
            .replaceAll("\r\n", "\n")
            .replaceAll("\n{1,}", " ")
            .replaceAll("(?m)^\\s+|\\s+$", "")
            .replaceAll(" {2,}", " ");
  }


  // 지정한 경로의 파일목록 Get
  private List<File> getJsonFiles(Path dir) throws IOException {
    return Files
            .walk(dir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().toLowerCase().endsWith(".json"))
            .map(Path::toFile)
            .collect(Collectors.toList());
  }

  private String getFileExtension(String fileName) {
    int idx = fileName.lastIndexOf('.');
    return (idx != -1) ? fileName.substring(idx) : "";
  }

  public String getFileNameOnly(String fileName) {
    int idx = fileName.lastIndexOf('.');
    return (idx != -1) ? fileName.substring(0, idx) : "";
  }


  // 입력,출력 경로를 받아 외부프로그램 수행하여 텍스트 추출
  public String convertWithExternalProgram(String inputFilePath, String outputFilePath) {

    File exePathFile = new File(SYNAP_EXE);
    String exePath = exePathFile.getAbsolutePath();

//    File workingDirFile = new File("D:/1.data_poc/synap_v4");
    File workingDirFile = new File(exePathFile.getParent());

    String workingDir = workingDirFile.getAbsolutePath();


    try {
      // 프로세스 명령어 구성
      ProcessBuilder processBuilder = new ProcessBuilder(
              exePath, "-U8", inputFilePath, outputFilePath
      );

      processBuilder.directory(new File(workingDir));
      processBuilder.redirectErrorStream(true); // stderr도 출력에 포함

      Process process = processBuilder.start();

      // 프로세스 출력 로그 확인 (필요 시)
      try (BufferedReader reader = new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          System.out.println("[snf_exe 출력] " + line);
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        System.err.println("프로세스 종료 코드: " + exitCode);
        return null;
      }

      // 결과 파일 읽기
      File outputFile = new File(outputFilePath);
      if (!outputFile.exists()) {
        System.err.println("출력 파일이 존재하지 않습니다: " + outputFilePath);
        return null;
      }

      // 파일 내용 읽어서 String에 저장
      StringBuilder contentBuilder = new StringBuilder();
      try (BufferedReader fileReader = new BufferedReader(
              new InputStreamReader(new FileInputStream(outputFile), StandardCharsets.UTF_8))) {
        String line;
        while ((line = fileReader.readLine()) != null) {
          contentBuilder.append(line).append(System.lineSeparator());
        }
      }

      return contentBuilder.toString();

    } catch (IOException | InterruptedException e) {
      System.err.println("외부 프로그램 실행 중 오류 발생: " + e.getMessage());
      return null;
    }
  }


  //압축파일 풀기
  private void extractZipFile(InputStream inputStream, String outputDir, String emid,
                              List<Map<String, Object>> resultList, EmailMeta meta, AtomicInteger counter) throws IOException {
    try (ZipInputStream zis = new ZipInputStream(inputStream)) {
      ZipEntry entry;
      byte[] buffer = new byte[1024];

      while ((entry = zis.getNextEntry()) != null) {
        File outFile = new File(outputDir + File.separator + emid.replace(":", "-"), entry.getName());
        outFile.getParentFile().mkdirs();

        if (entry.getName().toLowerCase().endsWith(".zip")) {
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          int len;
          while ((len = zis.read(buffer)) > 0) baos.write(buffer, 0, len);
          extractZipFile(new ByteArrayInputStream(baos.toByteArray()), outFile.getParent(), emid, resultList, meta, counter);
        } else {

          try (FileOutputStream fos = new FileOutputStream(outFile)) {
            int len;
            while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
          }
          String attachId = meta.getEmid() + "_attach_" + counter.getAndIncrement();
          resultList.add(createTargetMap(meta, attachId, outFile.getParent(), outFile.getName(), "", "", "N"));
        }
      }
    }
  }

  private Map<String, Object> createTargetMap(EmailMeta meta, String attachId, String attachPath, String attachName, String attachBody, String attachExist, String linkYn) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("attach_id", attachId);
    map.put("attach_path", attachPath);
    map.put("attach_name", attachName);
    map.put("attach_body", attachBody);
    map.put("attach_exist", attachExist);
    map.put("link_yn", linkYn);
    map.put("em_id", meta.getEmid());
    map.put("subject", meta.getSubject());
    map.put("sender", meta.getSender());
    map.put("senddtm", meta.getSenddtm());
    map.put("receiver", meta.getReceiver());
    map.put("idxdtm", meta.getIdxdtm());
    map.put("em_body", meta.getBody());
    return map;
  }

  private LGFileMailVO createVOFromFile(LGFileMailVO base, File file, String emid, int index, String exist, String linkYn) {
    String attachId = emid + "_attach_" + index;

    return LGFileMailVO.builder()
                       .key(attachId)
                       .attach_id(attachId)
                       .attach_path(file.getAbsolutePath())
                       .attach_name(file.getName())
                       .attach_body("") // 사이냅 처리 전이므로 비움
                       .attach_exist(exist)
                       .attach_parent(base.getAttachFile().getFileContentPath())    //압축파일인경우 부모압축파일 경로
                       .from_zipfile(base.getAttachFile() !=null ? "Y" : "N" ) //부모가 압축파일인경우 Y
                       .link_yn(linkYn)
                       .em_id(emid)
                       .subject(base.getSubject())
                       .sender(base.getSender())
                       .senddtm(base.getSenddtm())
                       .receiver(base.getReceiver())
                       .idxdtm(base.getIdxdtm())
                       .em_body(base.getEm_body())
                       .email(base.getEmail())
                       .attachFile(base.getAttachFile())
                       .build();
  }


  private Map<String, Object> createAttachMap(String emid, AtomicInteger counter, File file, String exist, String linkYn) {
    Map<String, Object> map = new HashMap<>();
    String attachId = emid + "_attach_" + counter.getAndIncrement();

    map.put("attach_id", attachId);
    map.put("attach_path", file.getAbsolutePath());
    map.put("attach_name", file.getName());
    map.put("attach_body", ""); // 사이냅 처리 전이므로 비움
    map.put("attach_exist", exist);
    map.put("link_yn", linkYn);
    map.put("emid", emid);

    return map;
  }


//맵 attach의 attaches가 String일때 사용.
public List<LGFileMailVO> checkFile_Unzip_if_Zipfile(List<LGFileMailVO> result) throws IOException {
  List<LGFileMailVO> totalResult = new ArrayList<>();

  for (LGFileMailVO vo : result) {
    String attachPath = vo.getAttach_path();
    String emid = vo.getEm_id();
    String originalKey = vo.getKey();
    AtomicInteger fileIndex = new AtomicInteger(1); // 압축 안에서만 사용하는 카운터

    // 첨부파일 없음
    if (attachPath == null || attachPath.isBlank()) {
      vo.setKey(originalKey);
      vo.setAttach_id(originalKey);  // ✅ key = attach_id
      totalResult.add(vo);
      continue;
    }

    File attachFile = new File(attachPath);
    String ext = getFileExtension(attachPath);

    // 일반 파일
    if (!".zip".equalsIgnoreCase(ext)) {
      LGFileMailVO newVO = createVOFromFile(vo, attachFile, emid, 0,
              attachFile.exists() ? "Y" : "N", "");

      newVO.setKey(originalKey);
      newVO.setAttach_id(originalKey); // ✅
      totalResult.add(newVO);
      continue;
    }

    // ZIP 파일
    if (!attachFile.exists()) {
      LGFileMailVO newVO = createVOFromFile(vo, attachFile, emid, 0, "N", "N");
      newVO.setKey(originalKey);
      newVO.setAttach_id(originalKey); // ✅
      totalResult.add(newVO);
      continue;
    }

    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(attachFile))) {
      File tempDir = new File(attachFile.getParent(), attachFile.getName() + "_unzipped");
      if (!tempDir.exists()) tempDir.mkdirs();

      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;

        File outFile = new File(tempDir, entry.getName());
        outFile.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
          byte[] buffer = new byte[4096];
          int len;
          while ((len = zis.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
          }
        }

        if (getFileExtension(outFile.getName()).equalsIgnoreCase(".zip")) {
          LGFileMailVO nestedVO = LGFileMailVO.builder()
                                              .em_id(emid)
                                              .attach_path(outFile.getAbsolutePath())
                                              .email(vo.getEmail())
                                              .key(originalKey) // 유지
                                              .build();

          List<LGFileMailVO> nestedList = Collections.singletonList(nestedVO);
          List<LGFileMailVO> extracted = checkFile_Unzip_if_Zipfile(nestedList);

          for (LGFileMailVO extractedVO : extracted) {
            String newKey = originalKey + "_" + fileIndex.getAndIncrement();
            extractedVO.setKey(newKey);
            extractedVO.setAttach_id(newKey); // ✅ key와 동일하게
            totalResult.add(extractedVO);
          }

        } else {
          LGFileMailVO newVO = createVOFromFile(vo, outFile, emid, 0, "Y", "");
          String newKey = originalKey + "_" + fileIndex.getAndIncrement();
          newVO.setKey(newKey);
          newVO.setAttach_id(newKey); // ✅
          totalResult.add(newVO);
        }
      }
    } catch (IOException e) {
      System.err.println("ZIP 해제 실패: " + attachFile.getAbsolutePath());
      e.printStackTrace();
    }
  }

  return totalResult;
}

/*
  //리스트인경우 사용.  앱 attach의 attaches가 리스트일때사용
  public List<Map<String, Object>> unzipFromAllZip_EachJson_List(Map<String, Object> attach) throws IOException {
    List<Map<String, Object>> resultList = new ArrayList<>();

    String emid = (String) attach.get("emid");
    List<String> attaches = (List<String>) attach.get("attaches");
    AtomicInteger counter = new AtomicInteger(1);

    if (attaches == null || attaches.isEmpty()) return resultList;

    for (String attachPath : attaches) {
      File attachFile = new File(attachPath);
      String ext = getFileExtension(attachPath);

      if (!".zip".equalsIgnoreCase(ext)) {
        // 일반 파일인 경우 바로 결과에 추가
        resultList.add(createAttachMap(emid, counter, attachFile, "Y", ""));
      } else {
        // ZIP 파일인 경우 압축 해제 후 재귀적으로 탐색
        if (!attachFile.exists()) {
          resultList.add(createAttachMap(emid, counter, attachFile, "N", "N"));
          continue;
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(attachFile))) {
          File tempDir = new File(attachFile.getParent(), attachFile.getName() + "_unzipped");
          if (!tempDir.exists()) tempDir.mkdirs();

          ZipEntry entry;
          while ((entry = zis.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;

            File outFile = new File(tempDir, entry.getName());
            outFile.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
              byte[] buffer = new byte[4096];
              int len;
              while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
              }
            }

            // 재귀 여부 판단
            if (getFileExtension(outFile.getName()).equalsIgnoreCase(".zip")) {
              Map<String, Object> nestedAttach = new HashMap<>();
              nestedAttach.put("emid", emid);
              nestedAttach.put("attaches", Collections.singletonList(outFile.getAbsolutePath()));
              resultList.addAll(unzipFromAllZip_EachJson(nestedAttach));
            } else {
              resultList.add(createAttachMap(emid, counter, outFile, "Y", ""));
            }
          }
        } catch (IOException e) {
          // 압축 해제 중 오류 -> 로그 후 스킵
          System.err.println("ZIP 해제 실패: " + attachFile.getAbsolutePath());
          e.printStackTrace();
        }
      }
    }

    return resultList;
  }
*/


/*
  List<Map<String, Object>> unzipFromAllZip_EachJson(Map<String, Object> attach) throws IOException {
    List<Map<String, Object>> resultList = new ArrayList<>();

    EmailMeta meta = EmailMeta.from(attach);
    AtomicInteger counter = new AtomicInteger(1);
    List<String> attaches = (List<String>) email.get("attaches");

    resultList.add(createTargetMapFromMeta(meta));
    if (attaches == null || attaches.isEmpty()) return resultList;

    for (String attachName : attaches) {
      File attachFile = resolveAttachFile(attachName);
      String ext = getFileExtension(attachName);

      if (!".zip".equalsIgnoreCase(ext)) {
        String attachId = meta.getEmid() + "_attach_" + counter.getAndIncrement();
        String body = "";
        resultList.add(createTargetMap(meta, attachId, attachFile.getParent(), attachFile.getName(), body, "", ""));
      } else {
        if (attachFile.exists()) {
          try (InputStream zipStream = new FileInputStream(attachFile)) {
            extractZipFile(zipStream, attachFile.getParent(), meta.getEmid(), resultList, meta, counter);
          } catch (IOException e) {
            log.warn("Zip 파일 처리 중 오류 발생 (스킵): {}", attachFile.getAbsolutePath(), e);
          }
        } else {
          String attachId = meta.getEmid() + "_attach_" + counter.getAndIncrement();
          String body = "";
          resultList.add(createTargetMap(meta, attachId, attachFile.getParent(), attachFile.getName(), body, "N", "N"));
        }
      }
    }

    return resultList;
  }*/
}
