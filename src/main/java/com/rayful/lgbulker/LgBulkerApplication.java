package com.rayful.lgbulker;

import com.rayful.lgbulker.runner.TrueEmailJsonMakerRunner;
import com.rayful.lgbulker.runner.TrueFileJsonMakerRunner;
import com.rayful.lgbulker.runner.TrueFileJsonMakerRunnerWithPng;
import com.rayful.lgbulker.service.MailAttachService;
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
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static com.rayful.lgbulker.util.FileUtils.deleteDirectoryRecursively;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
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
  @Value("${app.paths.input.raw_emails}")
  private String RAWEMAILS_DIR;
  @Value("${app.paths.input.emails}")
  private String OUTPUT_DIR;

  private final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
  private final String CURRENT_TIME = LocalDateTime.now().format(FORMATTER);

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(LgBulkerApplication.class);
    app.setWebApplicationType(WebApplicationType.NONE);

    boolean isEmailMakerMode = Arrays.stream(args)
            .anyMatch(arg -> arg.equalsIgnoreCase("--mode=emailmaker"));

    boolean isFileMakerMode = Arrays.stream(args)
            .anyMatch(arg -> arg.equalsIgnoreCase("--mode=filemaker"));

    boolean isFileMakerMdePng = Arrays.stream(args)
            .anyMatch(arg -> arg.equalsIgnoreCase("--mode=filemakerpng"));

    // 이메일|파일의 원본소스파일이 비정상json -> 정상 json 형식으로 가공하는 코드 실행, LgBulkerApplication_emailMaker, LgBulkerApplication_fileMaker
    if (isEmailMakerMode || isFileMakerMode || isFileMakerMdePng) {
      var ctx = app.run(args);

      if (isEmailMakerMode) {
        ctx.getBean(TrueEmailJsonMakerRunner.class).run(ctx.getBean(ApplicationArguments.class));
      }

      if (isFileMakerMode) {
        ctx.getBean(TrueFileJsonMakerRunner.class).run(ctx.getBean(ApplicationArguments.class));
      }

      if (isFileMakerMdePng) {
        ctx.getBean(TrueFileJsonMakerRunnerWithPng.class).run(ctx.getBean(ApplicationArguments.class));
      }

    } // 메일.파일json 읽어 -> 처리 -> 검색엔진에 색인처리
    else {
      app.run(args);
    }

    System.exit(0);
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    long start = System.currentTimeMillis();
    log.info("------------------------------------------");
    log.info(" Bulker Start");
    log.info("------------------------------------------");

    List<String> indexModes = args.getOptionValues("indexmode");
    if (indexModes != null && !indexModes.isEmpty()) {
      String mode = indexModes.get(0).toLowerCase();

      if ("all".equals(mode) || "inc".equals(mode)) {
        log.info("====================데이터 로드 시작================");
        clearWorkingDir();

        mailAttachService.load();
        log.info("====================데이터 로드 완료===============");

        Thread.sleep(2000);
        log.info("====================bulk file 생성 시작================");
        mailAttachService.createBulkFiles();
        log.info("====================bulk file 생성완료 ================");

        Thread.sleep(2000);
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
