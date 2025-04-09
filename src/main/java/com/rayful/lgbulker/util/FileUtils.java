package com.rayful.lgbulker.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Slf4j
public class FileUtils {

  public static void deleteDirectoryRecursively(Path path) throws IOException {
    if (!Files.exists(path)) {
      log.info("삭제할 경로가 존재하지 않습니다: " + path);
      return;
    }

    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file); // 파일 삭제
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir); // 디렉토리 삭제
        return FileVisitResult.CONTINUE;
      }
    });

    log.info("작업폴더 정리완료: " + path);
  }

  // 사용 예시
  public static void main(String[] args) {
    Path targetPath = Paths.get("D:/1.data_poc/attachments/unzipped");

//    try {
//      deleteDirectoryRecursively(targetPath);
//    } catch (IOException e) {
//      e.printStackTrace();
//    }


    String filePath = "D:\\1.data_poc\\attachments";
    String fileName = "Generics관련.ppt";
    String output = getFileNameOnly(fileName);
    System.out.println(output);

  }

  public static String getFileExtension(String fileName) {
    int idx = fileName.lastIndexOf('.');
    return (idx != -1) ? fileName.substring(idx) : "";
  }

  public static String getFileNameOnly(String fileName) {
    int idx = fileName.lastIndexOf('.');
    return (idx != -1) ? fileName.substring(0, idx) : "";
  }
}

