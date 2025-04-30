package com.rayful.lgbulker.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LGEmailVo {

  @JsonProperty("Mail_DestGroup")
  private String mailDestGroup;

  @JsonProperty("Mail_Type")
  private String mailType;

  @JsonProperty("Mail_ClientContentPlain")
  private String mailClientContentPlain;

  @JsonProperty("Mail_SenderUID")
  private String mailSenderUID;

  @JsonProperty("Mail_CIP")
  private String mailCIP;

  @JsonProperty("Mail_To")
  private String mailTo;

  @JsonProperty("Mail_DestName")
  private String mailDestName;

  @JsonProperty("Mail_PolicyName")
  private String mailPolicyName;

  @JsonProperty("Mail_CPort")
  private String mailCPort;

  @JsonProperty("Mail_CC")
  private String mailCC;

  @JsonProperty("Mail_UID")
  private String mailUID;

  @JsonProperty("Mail_GUID")
  private String mailGUID;

  @JsonProperty("Mail_From")
  private String mailFrom;

  @JsonProperty("Mail_ViewType")
  private String mailViewType;

  @JsonProperty("Mail_FileCount")
  private String mailFileCount;

  @JsonProperty("Mail_SIP")
  private String mailSIP;

  @JsonProperty("Mail_Tag")
  private String mailTag;

  @JsonProperty("Mail_PolicyAction")
  private String mailPolicyAction;

  @JsonProperty("Mail_SenderDID")
  private String mailSenderDID;

  @JsonProperty("Mail_SPort")
  private String mailSPort;

  @JsonProperty("Mail_ReceiverUID")
  private String mailReceiverUID;

  @JsonProperty("Mail_DestGuid")
  private String mailDestGuid;

  @JsonProperty("Mail_CategoryType")
  private String mailCategoryType;

  @JsonProperty("Mail_TotalSize")
  private String mailTotalSize;

  @JsonProperty("Mail_Header")
  private String mailHeader;

  @JsonProperty("Mail_SrcGuid")
  private String mailSrcGuid;

  @JsonProperty("Mail_Subject")
  private String mailSubject;

  @JsonProperty("Mail_ServerContentPlain")
  private String mailServerContentPlain;

  @JsonProperty("Mail_FileList")
  private String mailFileList;

  @JsonProperty("Mail_SendTime")
  private String mailSendTime;

  @JsonProperty("Mail_ServerContentDec")
  private String mailServerContentDec;

  @JsonProperty("Mail_UName")
  private String mailUName;

  @JsonProperty("Mail_ClientSize")
  private String mailClientSize;

  @JsonProperty("Mail_RcptTo")
  private String mailRcptTo;

  @JsonProperty("Mail_SrcName")
  private String mailSrcName;

//  @JsonProperty("Mail_AbnormalNested")
//  private List<MailAbnormalNested> mailAbnormalNested;

  @JsonProperty("Mail_DID")
  private String mailDID;

  @JsonProperty("Mail_PolicyType")
  private String mailPolicyType;

  @JsonProperty("Mail_PolicyTrace")
  private String mailPolicyTrace;

  @JsonProperty("Mail_ServerSize")
  private String mailServerSize;

  @JsonProperty("Mail_SrcGroup")
  private String mailSrcGroup;
}