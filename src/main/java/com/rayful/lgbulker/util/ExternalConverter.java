package com.rayful.lgbulker.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
public class ExternalConverter {

  @Value("${app.paths.synap_exe}") private String SYNAP_EXE;

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
}
