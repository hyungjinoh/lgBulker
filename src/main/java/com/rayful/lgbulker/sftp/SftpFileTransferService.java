package com.rayful.lgbulker.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.Vector;

@Slf4j
@Service
@RequiredArgsConstructor
public class SftpFileTransferService {

  @Value("${sftp.host}")
  private String sftpHost;

  @Value("${sftp.port}")
  private int sftpPort;

  @Value("${sftp.username}")
  private String sftpUsername;

  @Value("${sftp.password}")
  private String sftpPassword;

  @Value("${sftp.remote-directory}")
  private String remoteDirectory;

  @Value("${sftp.local-directory}")
  private String localDirectory;

  public void transferFiles() {
    Session session = null;
    ChannelSftp channelSftp = null;

    try {
      log.info("Starting SFTP connection to {}:{}", sftpHost, sftpPort);

      JSch jsch = new JSch();
      session = jsch.getSession(sftpUsername, sftpHost, sftpPort);
      session.setPassword(sftpPassword);

      Properties config = new Properties();
      config.put("StrictHostKeyChecking", "no");
      session.setConfig(config);

      session.connect();
      channelSftp = (ChannelSftp) session.openChannel("sftp");
      channelSftp.connect();

      // 시작점: 지정된 remoteDirectory 기준으로 재귀 다운로드 시작
      downloadRecursive(channelSftp, remoteDirectory, localDirectory);

      log.info("SFTP file transfer completed.");

    } catch (Exception e) {
      log.error("SFTP file transfer failed: {}", e.getMessage(), e);
    } finally {
      if (channelSftp != null && channelSftp.isConnected()) {
        channelSftp.disconnect();
      }
      if (session != null && session.isConnected()) {
        session.disconnect();
      }
    }
  }

  private void downloadRecursive(ChannelSftp sftp, String remoteDir, String localDir) throws Exception {
    Vector<ChannelSftp.LsEntry> fileList = sftp.ls(remoteDir);

    File localDirFile = new File(localDir);
    if (!localDirFile.exists()) {
      localDirFile.mkdirs();
    }

    for (ChannelSftp.LsEntry entry : fileList) {
      String filename = entry.getFilename();
      if (filename.equals(".") || filename.equals("..")) continue;

      String remotePath = remoteDir + "/" + filename;
      String localPath = localDir + "/" + filename;

      if (entry.getAttrs().isDir()) {
        // 재귀적으로 서브 디렉토리 처리
        downloadRecursive(sftp, remotePath, localPath);
      } else {
        File localFile = new File(localPath);

        long remoteMtime = (long) entry.getAttrs().getMTime() * 1000L;
        boolean shouldDownload = true;

        if (localFile.exists()) {
          long localMtime = localFile.lastModified();
          if (localMtime >= remoteMtime) {
            log.info("Skipping {} (no update)", remotePath);
            shouldDownload = false;
          }
        }

        if (shouldDownload) {
          log.info("Downloading file: {}", remotePath);
          try (FileOutputStream fos = new FileOutputStream(localFile)) {
            sftp.get(remotePath, fos);
          }
          localFile.setLastModified(remoteMtime);
          log.info("Downloaded and updated mtime: {}", localPath);
        }
      }
    }
  }
}
