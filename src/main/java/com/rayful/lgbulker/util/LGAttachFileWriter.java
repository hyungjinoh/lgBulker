package com.rayful.lgbulker.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rayful.lgbulker.vo.LGAttachVO;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class LGAttachFileWriter {

  private static final ObjectMapper objectMapper = new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT);

  private static final int MAX_ITEMS_PER_FILE = 100;

  public static void writeAsJsonFiles(List<LGAttachVO> dataList, String outputDirPath) {
    try {
      // 1. 디렉토리 생성
      File outputDir = new File(outputDirPath);
      if (!outputDir.exists()) {
        outputDir.mkdirs();
      }

      // 2. 날짜 기반 파일명 prefix
      String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

      int total = dataList.size();
      int fileIndex = 1;

      for (int start = 0; start < total; start += MAX_ITEMS_PER_FILE) {
        int end = Math.min(start + MAX_ITEMS_PER_FILE, total);
        List<LGAttachVO> sublist = dataList.subList(start, end);

        String fileName = String.format("attach_%s_part%d.json", timestamp, fileIndex++);
        File outputFile = new File(outputDir, fileName);

        objectMapper.writeValue(outputFile, sublist);
        System.out.println("저장 완료: " + outputFile.getAbsolutePath());
      }

    } catch (Exception e) {
      System.err.println("파일 저장 중 오류 발생: " + e.getMessage());
      e.printStackTrace();
    }
  }
}

