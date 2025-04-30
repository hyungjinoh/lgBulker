package com.rayful.lgbulker;

import com.rayful.lgbulker.runner.TrueEmailJsonMakerRunner;
import com.rayful.lgbulker.runner.TrueFileJsonMakerRunner;
import com.rayful.lgbulker.runner.TrueFileJsonMakerRunnerWithPng;
import com.rayful.lgbulker.service.MailAttachService;
import com.rayful.lgbulker.service.IndexService;
import com.rayful.lgbulker.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static com.rayful.lgbulker.util.FileUtils.deleteDirectoryRecursively;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
@MapperScan(basePackages = "com.rayful.lgbulker.mapper")
public class LgBulkerApplication implements ApplicationRunner {

  private final MailAttachService mailAttachService;
  private final IndexService indexService;

  @Value("${app.paths.input.emails}")
  private String EMAILS_DIR;
  @Value("${app.paths.output.merged}")
  private String MERGED_DIR;
  @Value("${app.paths.output.bulkfiles}")
  private String BULK_DIR;

  @Value("${app.file.timestamp}")
  private String TIMESTAMP_FILE;

  @Value("${app.file.lock}")
  private String LOCK_FILE;

  @Value("${app.paths.input.raw_emails}")
  private String RAWEMAILS_DIR;
  @Value("${app.paths.input.emails}")
  private String OUTPUT_DIR;

  private final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
  private final String CURRENT_TIME = LocalDateTime.now().format(FORMATTER);

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(LgBulkerApplication.class);
    app.setWebApplicationType(WebApplicationType.NONE);

    boolean isEmailMakerMode = Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("--mode=emailmaker"));
    boolean isFileMakerMode = Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("--mode=filemaker"));
    boolean isFileMakerModePng = Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("--mode=filemakerpng"));
    boolean isSftpMode = Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("--mode=sftp"));

    if (isEmailMakerMode || isFileMakerMode || isFileMakerModePng) {
      var ctx = app.run(args);

      ApplicationArguments appArgs = ctx.getBean(ApplicationArguments.class);

      if (isEmailMakerMode) {
        new TrueEmailJsonMakerRunner().run(appArgs);
      }

      if (isFileMakerMode) {
        new TrueFileJsonMakerRunner().run(appArgs);
      }

      if (isFileMakerModePng) {
        new TrueFileJsonMakerRunnerWithPng().run(appArgs);
      }

      System.exit(0);
    } else if (isSftpMode) {
      ConfigurableApplicationContext ctx = app.run(args);
      log.info("✅ SFTP mode started. Waiting for scheduled transfers...");
      ctx.getBean(com.rayful.lgbulker.sftp.SftpScheduler.class);
    } else {
      app.run(args);
      System.exit(0);
    }
  }


  @Override
  public void run(ApplicationArguments args) throws Exception {
    long start = System.currentTimeMillis();
    log.info("------------------------------------------");
    log.info(" Bulker Start");
    log.info("------------------------------------------");

    // ✅ 현재 시간 파일로 기록
    String indexDtm = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    Files.write(Paths.get(TIMESTAMP_FILE), indexDtm.getBytes(StandardCharsets.UTF_8));
    log.info("⏱️ Timestamp saved to {}: {}", TIMESTAMP_FILE, indexDtm);


    List<String> indexModes = args.getOptionValues("indexmode");
    if (indexModes != null && !indexModes.isEmpty()) {
      String mode = indexModes.get(0).toLowerCase();


      // ✅ LOCK 파일 경로 처리
      Path lockPath = Paths.get(LOCK_FILE);
      if ("all".equals(mode) || "inc".equals(mode)) {

        if (Files.exists(lockPath)) {
          log.warn("🚫 LOCK 파일이 존재합니다. 중복 실행 방지를 위해 프로그램을 종료합니다. ({})", LOCK_FILE);
          System.exit(1);
        }

        // ✅ LOCK 파일 생성
        Files.createFile(lockPath);
        log.info("🔒 LOCK 파일 생성됨: {}", LOCK_FILE);

        try {
          log.info("====================데이터 로드 시작================");
          clearWorkingDir();

          mailAttachService.load(indexDtm);
          log.info("====================데이터 로드 완료===============");

          Thread.sleep(2000);
          log.info("====================bulk file 생성 시작================");
          mailAttachService.createBulkFiles();
          log.info("====================bulk file 생성완료 ================");

          Thread.sleep(2000);
          log.info("====================ES에 색인요청 시작================");
          indexService.doIndexing();
          log.info("====================ES에 색인요청 완료 ================");
        } finally {
          // ✅ LOCK 파일 삭제
          Files.deleteIfExists(lockPath);
          log.info("🔓 LOCK 파일 삭제됨: {}", LOCK_FILE);
        }
      }
    }

    long duration = System.currentTimeMillis() - start;
    log.info("------------------------------------------");
    log.info(" Bulker End (" + Utils.getMsToTime(duration) + ")");
    log.info("------------------------------------------");
  }

  private void clearWorkingDir() {
    log.info("------------------------------------------");
    log.info("             작업 디렉토리 정리              ");
    log.info("------------------------------------------");

    try {
      Path emailsPath = Paths.get(EMAILS_DIR);
      Path attachesPath = Paths.get(MERGED_DIR);
      Path bulkPath = Paths.get(BULK_DIR);

      if (!Files.exists(emailsPath)) Files.createDirectory(emailsPath);
      if (!Files.exists(attachesPath)) Files.createDirectory(attachesPath);
      if (!Files.exists(bulkPath)) Files.createDirectory(bulkPath);

      deleteDirectoryRecursively(attachesPath);
      deleteDirectoryRecursively(bulkPath);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
