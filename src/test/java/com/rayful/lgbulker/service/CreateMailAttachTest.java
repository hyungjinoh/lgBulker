package com.rayful.lgbulker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rayful.lgbulker.mapper.EmailMapper;
import com.rayful.lgbulker.mapper.FileMapper;
import com.rayful.lgbulker.vo.LGFileVO;
import com.rayful.lgbulker.vo.LGEmailVo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;
import java.util.List;


/***
 * 테스트 클래스에서 데이터 내려받기 위해 사용하는 vo는  com.rayful.lgbulker.vo 패키지 아래의 클래스를 사용한다.
 */

@ActiveProfiles("local")
@SpringBootTest
class CreateMailAttachTest {

    @Autowired
    private EmailMapper emailMapper;

    @Autowired
    private FileMapper fileMapper;

    // --- 설정 값 주입 ---
    @Value("${app.bulk.size}")
    private int BATCH_SIZE;
    @Value("${app.paths.input.emails}")
    private String EMAILS_DIR;          //가. 처리대상 json 형식 이메일 파일들 경로
    @Value("${app.paths.input.files}")
    private String FILES_DIR;     //나. 처리대상 : 가. 를 첨부기준 json 형식으로 저장한 결과

    @Value("${app.paths.output.merged}")
    private String MERGED_DIR;     //다. 첨부+이메일 통합 파일 저장경로
    @Value("${app.paths.output.bulkfiles}")
    private String BULK_PATH;       //다. ES에 색인위한 bulk파일 저장경로

    @Test
    public void createMailJson() throws IOException {
        // Raw data 수집 및 생성 (이메일 기준) / from DB table
        List<LGEmailVo> emailVoList = emailMapper.findAll();

        File mailJson = new File(EMAILS_DIR, "lgemail.json"); // 메일 저장경로

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(String.valueOf(mailJson)), emailVoList);

        System.out.println("✅ lgemail.json 파일 저장 완료!");
    }

    @Test
    public void createFileJson() throws IOException {
        // Raw data 수집 및 생성 (이메일 기준) / from DB table
        List<LGFileVO> LGFileVoList = fileMapper.findAll();

        File fileJson = new File(FILES_DIR, "lgfile.json"); // 메일 저장경로

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File(String.valueOf(fileJson)), LGFileVoList);

        System.out.println("✅ lgemail.json 파일 저장 완료!");
    }

}