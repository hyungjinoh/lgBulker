package com.rayful.lgbulker.vo;

public class AttachVO {
  private String fileName;
  private String filePath;
  private long fileSize;

  public AttachVO(String fileName, String filePath, long fileSize) {
    this.fileName = fileName;
    this.filePath = filePath;
    this.fileSize = fileSize;
  }

  // Getter & Setter
  public String getFileName() { return fileName; }
  public String getFilePath() { return filePath; }
  public long getFileSize() { return fileSize; }

  @Override
  public String toString() {
    return "AttachVO{" +
            "fileName='" + fileName + '\'' +
            ", filePath='" + filePath + '\'' +
            ", fileSize=" + fileSize +
            '}';
  }
}

