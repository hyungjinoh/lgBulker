package com.rayful.lgbulker.vo;

import lombok.*;


@ToString
@Builder
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class AttachVO {
  private String fileName;
  private String filePath;
  private long fileSize;
  private String fileExist;

}

