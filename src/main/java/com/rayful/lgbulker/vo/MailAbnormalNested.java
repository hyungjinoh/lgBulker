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
public class MailAbnormalNested {

  @JsonProperty("Mail_MaskedAbnormalData")
  private String mailMaskedAbnormalData;

  @JsonProperty("Mail_AbnormalType")
  private int mailAbnormalType;

  @JsonProperty("Mail_AbnormalName")
  private String mailAbnormalName;

  @JsonProperty("Mail_AbnormalCount")
  private int mailAbnormalCount;

  @JsonProperty("Mail_AbTextNested")
  private List<MailAbTextNested> mailAbTextNested;

  @JsonProperty("Mail_EDMNested")
  private List<Object> mailEDMNested;

  @JsonProperty("Mail_AbnormalData")
  private String mailAbnormalData;

  @JsonProperty("Mail_ScanLogType")
  private int mailScanLogType;
}
