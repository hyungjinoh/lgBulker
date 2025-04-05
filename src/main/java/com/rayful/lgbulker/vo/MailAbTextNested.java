package com.rayful.lgbulker.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailAbTextNested {

  @JsonProperty("Mail_AbTextCount")
  private int mailAbTextCount;

  @JsonProperty("Mail_AbTextID")
  private String mailAbTextID;

  @JsonProperty("Mail_AbTextMasked")
  private String mailAbTextMasked;

  @JsonProperty("Mail_AbTextData")
  private String mailAbTextData;
}