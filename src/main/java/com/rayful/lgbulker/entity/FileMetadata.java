package com.rayful.lgbulker.entity;

import lombok.*;

import java.time.LocalDateTime;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileMetadata {
  private Long id;
  private String fileGuid;
  private String fileName;
  private Integer fileSize;
  private String mailGuid;
  private String fileContentPath;
  private LocalDateTime fileTime;
  private String errorCode;
  private String errorMessage;
}
