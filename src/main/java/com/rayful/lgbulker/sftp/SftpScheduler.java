package com.rayful.lgbulker.sftp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true", matchIfMissing = false) //명시적으로 실행시 지정해야 동작함. attach_copy.sh 참조
public class SftpScheduler {

  private final SftpFileTransferService sftpFileTransferService;

  @Scheduled(cron = "${sftp.cron}")
  public void scheduledFileTransfer() {
    log.info("Running scheduled SFTP file transfer...");
    sftpFileTransferService.transferFiles();
  }

  @PostConstruct
  public void initLog() {
    log.info("✅ SftpScheduler initialized because scheduler.enabled=true");
  }
}
