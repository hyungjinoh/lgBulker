package com.rayful.lgbulker.service;

import com.rayful.lgbulker.mapper.FileMetadataMapper;
import com.rayful.lgbulker.entity.FileMetadata;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/***
 * @MybatisTest는 기본적으로 테스트마다 트랜잭션을 자동으로 걸고, 테스트 끝나면 롤백해버려.
 * 즉, insert가 실제로 DB에 들어가긴 하지만, 테스트 메서드 끝나면 롤백돼서 사라지는 거야.
 *
 * 커밋하고 싶으면  @Rollback(false) 사용바람.
 */

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TestMybatisConfig.class)
public class FileMetadataMapperTest {

  @Autowired
  private FileMetadataMapper fileMetadataMapper;

  @Test
  @Rollback(false) // 🔥 이걸 추가하면 insert 이후 롤백 안 한다!
  void insertFileMetadataTest() {
    FileMetadata metadata = new FileMetadata();
    metadata.setFileGuid("test_file_guid");
    metadata.setFileName("testfile.zip");
    metadata.setFileSize(123);
    metadata.setMailGuid("test_mail_guid");
    metadata.setFileContentPath("/tmp/testfile.zip");
    metadata.setFileTime(LocalDateTime.now());
    metadata.setErrorCode("1");
    metadata.setErrorMessage("테스트용 에러 메시지");

    fileMetadataMapper.insertFileMetadata(metadata);

    assertThat(metadata.getId()).isNotNull(); // 자동 생성된 ID가 있는지 확인
  }
}
