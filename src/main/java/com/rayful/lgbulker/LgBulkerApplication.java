package com.rayful.lgbulker;

import com.rayful.lgbulker.service.LgMailAttachService;
import com.rayful.lgbulker.service.IndexService;
import com.rayful.lgbulker.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.rayful.lgbulker.util.FileUtils.deleteDirectoryRecursively;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class LgBulkerApplication implements ApplicationRunner {
  private final LgMailAttachService lgMailAttachService;
  private final IndexService indexService;

  @Value("${app.paths.input.emails}")
  private String EMAILS_DIR;

  @Value("${app.paths.output.merged}")
  private String MERGED_DIR;

  @Value("${app.paths.output.bulkfiles}")
  private String BULK_DIR;

  @Value("${app.file.timestamp}")
  private String TIMESTAMP_FILE;

  @Value("${app.paths.input.raw_emails}")
  private String RAWEMAILS_DIR;

  @Value("${app.paths.input.emails}")
  private String OUTPUT_DIR;

  private final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  private final String CURRENT_TIME = LocalDateTime.now().format(FORMATTER);


  public static void main(String[] args) {

    SpringApplication app = new SpringApplication(LgBulkerApplication.class);

    // 웹 서버 비활성화 (CLI 모드)
    app.setWebApplicationType(WebApplicationType.NONE);
    app.run(args);

    System.exit(0);
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {

    long start = System.currentTimeMillis();
    log.info("------------------------------------------");
    log.info(" Bulker Start");
    log.info("------------------------------------------");

    // 옵션 인자 확인 (예: --key=value 형태)
    List<String> indexModes = args.getOptionValues("indexmode");
    if (indexModes != null && !indexModes.isEmpty()) {
      String mode = indexModes.get(0).toLowerCase();

      if ("all".equals(mode) || "inc".equals(mode)) {

        log.info("====================데이터 로드 시작================");
        clearWorkingDir();

        lgMailAttachService.load();
        log.info("====================데이터 로드 완료===============");

        Thread.sleep(3000);
        log.info("====================bulk file 생성 시작================");
        lgMailAttachService.createBulkFiles();

        log.info("====================bulk file 생성완료 ================");

        Thread.sleep(3000);
        log.info("====================ES에 색인요청 시작================");
        indexService.doIndexing();

        log.info("====================ES에 색인요청 완료 ================");
      }
    }

    long duration = System.currentTimeMillis() - start;
    log.info("------------------------------------------");
    log.info(" Bulker End (" + Utils.getMsToTime(duration) + ")");
    log.info("------------------------------------------");

  }

  /***
   * 작업디렉토리 정리 :
   */
  private void clearWorkingDir() {
    log.info("------------------------------------------");
    log.info("             작업 디렉토리 정리              ");
    log.info("------------------------------------------");
    Path emailsPath = Paths.get(EMAILS_DIR);
    Path attachesPath = Paths.get(MERGED_DIR);
    Path bulkPath = Paths.get(BULK_DIR);

    try {

      if (!Files.exists(emailsPath)) {
        Files.createDirectory(emailsPath);
      }

      if (!Files.exists(attachesPath)) {
        Files.createDirectory(attachesPath);
      }
      if (!Files.exists(bulkPath)) {
        Files.createDirectory(bulkPath);
      }

      // 입력, email 경로 삭제
//      deleteDirectoryRecursively(emailsPath);
      // 결과(attaches 경로) 삭제
      deleteDirectoryRecursively(attachesPath);
      // 결과(bulk 경로) 삭제
      deleteDirectoryRecursively(bulkPath);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
