package com.rayful.lgbulker.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LGMailAttachServiceTest {

  @Test
  void createBulkFiles() {


  }


  @Test
  void testTypeA() {
    //    A, 파일없음-객체생성 및 처리,
    Map<String, Object> resultMapA = new HashMap<>();
    resultMapA.put("key", "unknown_m10");
    resultMapA.put("attach_id", "unknown_m10");
    resultMapA.put("attach_path", "");
    resultMapA.put("attach_parent", "");
    resultMapA.put("from_zipfile", "");
    resultMapA.put("attach_name", "");
    resultMapA.put("attach_exist", "");
    resultMapA.put("link_yn", "");
    resultMapA.put("subject", "명량A");
    resultMapA.put("bodyLinks", Arrays.asList(""));
  }


  @Test
  void testTypeB() {
//    B, 일반파일, 링크 없음-객체생성 및 처리
    Map<String, Object> resultMapB = new HashMap<>();
    resultMapB.put("key", "f2_m20");
    resultMapB.put("attach_id", "f2_m20");
    resultMapB.put("attach_path", "D:\\1.data_poc\\attachments\\(강의자료)_스프링_부트를_이용한__웹서비스_개발.pdf");
    resultMapB.put("attach_name", "(강의자료)_스프링_부트를_이용한__웹서비스_개발.pdf");
    resultMapB.put("subject", "명량B");
    resultMapB.put("bodyLinks", "");
  }

  @Test
  void testTypeC() {
//    C, 압축파일, 링크 없음
    Map<String, Object> resultMapC = new HashMap<>();
    resultMapC.put("key", "f3_m30");
    resultMapC.put("attach_id", "f3_m30");
    resultMapC.put("attach_path", "D:\\1.data_poc\\attachments\\자바강좌.zip");
    resultMapC.put("attach_name", "자바강좌.zip");
    resultMapC.put("bodyLinks", "");
    resultMapC.put("subject", "명량C");
  }


  @Test
  void testTypeD() {
//    D, 일반파일, 링크 있음
    Map<String, Object> resultMapD = new HashMap<>();
    resultMapD.put("key", "f4_m40");
    resultMapD.put("attach_id", "f4_m40");
    resultMapD.put("attach_path", "D:\\1.data_poc\\attachments\\제 12장.pdf");
    resultMapD.put("attach_name", "제 12장.pdf");
    resultMapD.put("bodyLinks", Arrays.asList("https://bigfile.mail.naver.com/download?fid=g/FcWrC516cwKCYZHquZKxvXFCYZKAU9KAtmaAurFxvmFIYwFqtrFqurKqvlHrMdKoKZFzFSaA04M4udKqtmMqUdaAgqKqbqpxbrKoMr" ));
    resultMapD.put("subject", "명량D");
  }

  @Test
  void testTypeE() {

//    E, 파일없음, 링크있음
    Map<String, Object> resultMapE = new HashMap<>();
    resultMapE.put("key", "unknown_m50");
    resultMapE.put("attach_id", "unknown_m50");
    resultMapE.put("attach_path", "");
    resultMapE.put("attach_name", "");
    resultMapE.put("bodyLinks", Arrays.asList("https://bigfile.mail.naver.com/download?fid=g/FcWrC516cwKCYZHquZKxvXFCYZKAU9KAtmaAurFxvmFIYwFqtrFqurKqvlHrMdKoKZFzFSaA04M4udKqtmMqUdaAgqKqbqpxbrKoMr"));
    resultMapE.put("subject", "명량E");
  }

  @Test
  void testTypeF() {
//    F, 압축파일, 링크 있음
    Map<String, Object> resultMapF = new HashMap<>();
    resultMapF.put("key", "f6_m60");
    resultMapF.put("attach_id", "f6_m60");
    resultMapF.put("attach_path", "D:\\1.data_poc\\attachments\\압축문서.zip");
    resultMapF.put("attach_name", "압축문서.zip");
    resultMapF.put("bodyLinks", Arrays.asList(
            "https://bigfile.mail.naver.com/download?fid=g/FcWrC516cwKCYZHquZKxvXFCYZKAU9KAtmaAurFxvmFIYwFqtrFqurKqvlHrMdKoKZFzFSaA04M4udKqtmMqUdaAgqKqbqpxbrKoMr"
            ));
    resultMapF.put("subject", "명량F");
  }
}