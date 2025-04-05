package com.rayful.lgbulker.vo;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@Builder
public class HtmlEmailVo {
  private String from;
  private String to;
  private String subject;
  private String receivedDate;
  private String body;             // → text/html 또는 fallback text/plain
  private String attaches_link;    // → 콤마로 분리된 다운로드 링크
}
