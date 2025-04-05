package com.rayful.lgbulker.vo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmailDto {
  private Long id;
  private String emid;
  private String subject;
  private String sender;
  private String receiver;
  private String senddtm;
  private String body;
  private String idxdtm;

  @JsonProperty("attaches")
  private List<String> attaches = new ArrayList<>();
}
