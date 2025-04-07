package com.rayful.lgbulker.module;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmailMeta {
  private String emid;
  private String sender;
  private String receiver;
  private String senddtm;
  private String subject;
  private String body;
  private String idxdtm;

  public static EmailMeta from(Map<String, Object> map) {
    String senddtm = (String) map.get("senddtm");
    String sender = (String) map.get("sender");
    String receiver = (String) map.get("receiver");
    String emid = senddtm.replace(" ", "T") + "_" + sender;

    return EmailMeta.builder()
                    .emid(emid)
                    .sender((String) map.get("sender"))
                    .receiver(receiver)
                    .senddtm(senddtm)
                    .subject((String) map.get("subject"))
                    .body((String) map.get("body"))
                    .idxdtm((String) map.get("idxdtm"))
                    .build();
  }
}
