package com.rayful.lgbulker.service;

import com.rayful.lgbulker.module.EmailMeta;
import com.rayful.lgbulker.util.FileUtils;
import com.rayful.lgbulker.vo.LGFileMailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

  @Value("${app.paths.attachments}")
  private String ATTACHMENTS_PATH;     //라. 첨부파일저장경로1

  @Value("${app.paths.synap_exe}")
  private String SYNAP_EXE;


  private String getFileExtension(String fileName) {
    int idx = fileName.lastIndexOf('.');
    return (idx != -1) ? fileName.substring(idx) : "";
  }



  //압축파일 풀기
  private void extractZipFile(InputStream inputStream, String outputDir, String emid, List<Map<String, Object>> resultList, EmailMeta meta, AtomicInteger counter) throws IOException {
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

    String attachFile;
    if (linkYn.equalsIgnoreCase("N")) {
      attachFile = base.getAttachFile().getFileContentPath();
    } else {
      attachFile = base.getAttach_path();
    }

    Boolean isZipfile = FileUtils.getFileExtension(attachFile).equalsIgnoreCase("zip");
    String parentFile = isZipfile ? attachFile : "";

    return LGFileMailVO
            .builder()
            .key(attachId)
            .attach_id(attachId)
            .attach_path(file.getAbsolutePath())
            .attach_name(file.getName())
            .attach_body("") // 사이냅 처리 전이므로 비움
            .attach_exist(exist)
//                       .attach_parent(base.getAttachFile().getFileContentPath())    //압축파일인경우 부모압축파일 경로
            .attach_parent(parentFile)    //압축파일인경우 부모압축파일 경로
//                       .from_zipfile(base.getAttachFile() !=null ? "Y" : "N" ) //부모가 압축파일인경우 Y
            .from_zipfile(isZipfile ? "Y" : "N") //부모가 압축파일인경우 Y
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


  //인자를 List<맵> 으로 받았을때 압축파일 풀고, 각 요소마다 별도의 attach_id, key 부여하는 코드
  // 아래 메서드 checkFile_Unzip_if_Zipfile(List<객체>) 메서드와 동일기능 수행함.
  public List<Map<String, Object>> checkMap_Unzip_if_Zipfile(List<Map<String, Object>> result) throws IOException {
    List<Map<String, Object>> finalResult = new ArrayList<>();

    for (Map<String, Object> map : result) {
      String attachPath = (String) map.get("attach_path");
      String emid = (String) map.get("em_id");
      String originalKey = (String) map.get("key");
      AtomicInteger fileIndex = new AtomicInteger(1);

      if (attachPath == null || attachPath.isBlank()) {
        map.put("key", originalKey);
        map.put("attach_id", originalKey);
        map.put("from_zipfile", "N");
        finalResult.add(map);
        continue;
      }

      File attachFile = new File(attachPath);
      String ext = getFileExtension(attachPath);
      String fileName = attachFile.getName();

      if (!".zip".equalsIgnoreCase(ext)) {
        Map<String, Object> newMap = new HashMap<>(map);
        newMap.put("attach_exist", attachFile.exists() ? "Y" : "N");
        newMap.put("from_zipfile", "N");
        newMap.put("key", originalKey);
        newMap.put("attach_id", originalKey);
        newMap.put("attach_name", fileName);
        finalResult.add(newMap);
        continue;
      }

      // ZIP 파일인데 존재하지 않으면 추가
      if (!attachFile.exists()) {
        Map<String, Object> newMap = new HashMap<>(map);
        newMap.put("attach_exist", "N");
        newMap.put("from_zipfile", "Y");
        newMap.put("attach_parent", attachPath);
        newMap.put("key", originalKey);
        newMap.put("attach_id", originalKey);
        finalResult.add(newMap);
        continue;
      }

      // 압축 해제
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

          String extOut = getFileExtension(outFile.getName());
          String newKey = originalKey + "_" + fileIndex.getAndIncrement();

          if (".zip".equalsIgnoreCase(extOut)) {
            Map<String, Object> nestedMap = new HashMap<>();
            nestedMap.put("em_id", emid);
            nestedMap.put("attach_path", outFile.getAbsolutePath());
            nestedMap.put("email", map.get("email"));
            nestedMap.put("key", newKey);

            List<Map<String, Object>> nestedList = Collections.singletonList(nestedMap);
            List<Map<String, Object>> extracted = checkMap_Unzip_if_Zipfile(nestedList);

            for (Map<String, Object> extractedMap : extracted) {
              extractedMap.put("key", extractedMap.get("key"));
              extractedMap.put("attach_id", extractedMap.get("key"));
              extractedMap.put("attach_parent", attachPath);
              extractedMap.put("from_zipfile", "Y");
              finalResult.add(extractedMap);
            }
          } else {
            Map<String, Object> newMap = new HashMap<>(map);
            newMap.put("attach_path", outFile.getAbsolutePath());
            newMap.put("attach_exist", "Y");
            newMap.put("attach_name", outFile.getName());
            newMap.put("key", newKey);
            newMap.put("attach_id", newKey);
            newMap.put("attach_parent", attachPath);
            newMap.put("from_zipfile", "Y");
            finalResult.add(newMap);
          }
        }
      } catch (IOException e) {
        System.err.println("ZIP 해제 실패: " + attachFile.getAbsolutePath());
        e.printStackTrace();
      }
    }

    return finalResult;
  }


  //맵 attach의 attaches가 String일때 사용.

  /***
   * 첨부파일이 .zip 확장자일 경우 재귀적으로 압축을 해제하고,
   * 내부에 있는 개별 파일을 LGFileMailVO로 변환해서 리턴함.
   * @return List<LGFileMailVO>
   */
  public List<LGFileMailVO> checkFile_Unzip_if_Zipfile(List<LGFileMailVO> result) throws IOException {
    List<LGFileMailVO> totalResult = new ArrayList<>();

    for (LGFileMailVO vo : result) {
      String attachPath = vo.getAttach_path();
      String emid = vo.getEm_id();
      String originalKey = vo.getKey();
      AtomicInteger fileIndex = new AtomicInteger(1);

      if (attachPath == null || attachPath.isBlank()) {
        vo.setKey(originalKey);
        vo.setAttach_id(originalKey);
        vo.setFrom_zipfile("N");
        totalResult.add(vo);
        continue;
      }

      File attachFile = new File(attachPath);
      String ext = getFileExtension(attachPath);

      if (!".zip".equalsIgnoreCase(ext)) {
        LGFileMailVO newVO = createVOFromFile(vo, attachFile, emid, 0, attachFile.exists() ? "Y" : "N", "N");

        newVO.setKey(originalKey);
        newVO.setAttach_id(originalKey);
        newVO.setFrom_zipfile("N");
        totalResult.add(newVO);
        continue;
      }

      if (!attachFile.exists()) {
        LGFileMailVO newVO = createVOFromFile(vo, attachFile, emid, 0, "N", "N");
        newVO.setKey(originalKey);
        newVO.setAttach_id(originalKey);
        newVO.setFrom_zipfile("N");
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
            LGFileMailVO nestedVO = LGFileMailVO
                    .builder()
                    .em_id(emid)
                    .attach_path(outFile.getAbsolutePath())
                    .email(vo.getEmail())
                    .key(originalKey)
                    .build()
                    ;

            List<LGFileMailVO> nestedList = Collections.singletonList(nestedVO);
            List<LGFileMailVO> extracted = checkFile_Unzip_if_Zipfile(nestedList);

            for (LGFileMailVO extractedVO : extracted) {
              String newKey = originalKey + "_" + fileIndex.getAndIncrement();
              extractedVO.setKey(newKey);
              extractedVO.setAttach_id(newKey);
              extractedVO.setAttach_parent(attachPath);
              extractedVO.setFrom_zipfile("Y");
              totalResult.add(extractedVO);
            }

          } else {
            LGFileMailVO newVO = createVOFromFile(vo, outFile, emid, 0, "Y", "Y");
            String newKey = originalKey + "_" + fileIndex.getAndIncrement();
            newVO.setKey(newKey);
            newVO.setAttach_id(newKey);
            newVO.setAttach_parent(attachPath);
            newVO.setFrom_zipfile("Y");
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
}
