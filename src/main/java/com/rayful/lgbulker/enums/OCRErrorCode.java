package com.rayful.lgbulker.enums;

public enum OCRErrorCode {
  OCR_API_CALL_FAIL("1001", "OCR API 호출 실패"),
  OCR_API_PROCESS_FAIL("1002", "OCR API 요청 처리중 에러");


  private final String code;
  private final String message;

  OCRErrorCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String getCode() { return code; }
  public String getMessage() { return message; }
}
