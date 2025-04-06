package com.rayful.lgbulker.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileVO {

  @JsonProperty("File_AbnormalNested")
  private List<Object> fileAbnormalNested;

  @JsonProperty("File_GUID")
  private String fileGUID;

  @JsonProperty("File_Name")
  private String fileName;

  @JsonProperty("File_Size")
  private String fileSize;

  @JsonProperty("Mail_GUID")
  private String mailGUID;

  @JsonProperty("File_ContentPath")
  private String fileContentPath;

  @JsonProperty("File_Time")
  private String fileTime;

  @JsonProperty("File_DownloadLink")
  private String fileDownloadLink;
}
