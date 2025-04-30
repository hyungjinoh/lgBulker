package com.rayful.lgbulker.service;

import com.rayful.lgbulker.mapper.FileMetadataMapper;
import com.rayful.lgbulker.entity.FileMetadata;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.*;

import java.io.IOException;
import java.io.Reader;

// ✅ 추가로 필요한 import
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.mapping.Environment;

public class FileMetadataMapperManualTest {

  public static void main(String[] args) throws IOException {
    // 1. DataSource 수동 구성
    PooledDataSource dataSource = new PooledDataSource();
    dataSource.setDriver("com.mysql.cj.jdbc.Driver");
    dataSource.setUrl("jdbc:mysql://122.199.202.101:3306/lgcns_admin");
    dataSource.setUsername("root");
    dataSource.setPassword("pass123#");

    // 2. MyBatis 설정 읽기
    Reader reader = Resources.getResourceAsReader("mybatis-config.xml");

// 3. SqlSessionFactory 생성
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);


    // ❗ 여기 수정: Environment를 직접 세팅
    TransactionFactory transactionFactory = new JdbcTransactionFactory();
    Environment environment = new Environment("manual", transactionFactory, dataSource);
    sqlSessionFactory.getConfiguration().setEnvironment(environment);

    try (SqlSession session = sqlSessionFactory.openSession(true)) {
      FileMetadataMapper mapper = session.getMapper(FileMetadataMapper.class);


      FileMetadata metadata = FileMetadata.builder()
                  .fileGuid("manual_test_guid")
                  .fileName("test.zip")
                  .fileSize(111)
                  .mailGuid("manual_mail_guid")
                  .fileContentPath("/tmp/test.zip")
                  .errorCode("100")
                  .errorMessage("error")
                  .build();

      mapper.insertFileMetadata(metadata);
      System.out.println("✅ Insert 성공: ID = " + metadata.getId());
    }
  }
}
