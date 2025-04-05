package com.rayful.lgbulker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rayful.lgbulker.mapper.EmailMapper;
import com.rayful.lgbulker.vo.LGEmailVo;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;
import java.util.List;


@ActiveProfiles("local")
@SpringBootTest
class CreateMailAttachTest {

  @Autowired
  private EmailMapper emailMapper;

  @Test
  public void createMailAttach() throws IOException {
    // Raw data 수집 및 생성 (이메일 기준) / from DB table
    List<LGEmailVo> emailVoList = emailMapper.findAll();

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File("lgemail.json"), emailVoList);

    System.out.println("✅ lgemail.json 파일 저장 완료!");
  }

}