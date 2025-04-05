package com.rayful.lgbulker.vo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rayful.lgbulker.util.Utils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LGAttachVO {
  private String key; // fileGUID_mailGUID 또는 unknown_mailGUID
  //////////////////////////
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

  private AttachFileVo attachFile;
  private LGEmailVo email;


  // ✅ 필드 자동 세팅 메서드
  public void fillDerivedFields() {
    this.attach_id = this.key;
    //첨부경로
    this.attach_path = (attachFile != null) ? attachFile.getFileContentPath() : null;
    this.attach_name = (attachFile != null) ? attachFile.getFileName() : null;
    //첨부내용, 사이냅 처리후 할당
    this.attach_body = (attachFile != null) ? attachFile.getFileName() : null;
    //첨부존재여부
    this.attach_exist = (attachFile != null) ? "Y" : "N";


    // 대용량에서 첨부여부
    this.link_yn = "";
    this.em_id = email.getMailGUID();
    this.subject = (email != null) ? email.getMailSubject() : null;
    this.sender = email.getMailFrom();
//    this.senddtm = Utils.convertDateFormat(email.getMailSendTime());
    this.senddtm = email.getMailSendTime().replace(" ", "T");
    this.receiver = email.getMailTo();
//    this.idxdtm = "";
    this.em_body = email.getMailClientContentPlain();

  }

  // ✅ Pretty JSON 출력 메서드
  public String toPrettyJson() {
    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    try {
      return mapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      return "JSON 변환 실패: " + e.getMessage();
    }
  }


}