package com.rayful.lgbulker.module;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmailVo {
  private Long id;
  private String emid;
  private String subject;
  private String sender;
  private String receiver;
  private String senddtm;
  private String body;
  private String idxdtm;
//  private List<String> attaches = new ArrayList<>();
  private String attaches; // "file1.txt, file2.doc" 형태로 받음

  public static EmailDto from(EmailVo emailVo) {
    String senddtm = (String) emailVo.getSenddtm();
    String sender = (String) emailVo.getSender();
    String receiver = (String) emailVo.getReceiver();
    String subject = (String) emailVo.getSubject();
    String body = (String) emailVo.getBody();
    String emid = senddtm.replace(" ", "T") + "_" + sender;

    List<String> attaches = new ArrayList<>();

    // 콤마형식의 String -> List<String>으로 변환
    String strAttaches = emailVo.getAttaches();
    if (strAttaches == null || strAttaches.isBlank()) {
      attaches = Collections.emptyList();
    } else {
      attaches = Arrays.stream(strAttaches.split(","))
            .map(String::trim)                    // 앞뒤 공백 제거
            .filter(s -> !s.isEmpty())            // 빈 문자열 제거
            .collect(Collectors.toList());

    }

            // null이면 빈 리스트 반환

    return EmailDto.builder()
                    .emid(emid)
                    .sender(sender)
                    .receiver(receiver)
                    .senddtm(senddtm)
                    .subject(subject)
                    .body(body)
                    .idxdtm("")
                    .attaches(attaches)
                    .build();
  }
}
