package com.rayful.lgbulker.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttachVo {
  private String attach_id;
  private String attach_path;
  private String attach_name;
  private String attach_body;
  private String attach_exist;
  private String link_yn;
  private String em_id;
  private String subject;
  private String sender;
  private String senddtm;
  private String receiver;
  private String idxdtm;
  private String em_body;
}



/*
attach_id = LGAttachVO.key

 */