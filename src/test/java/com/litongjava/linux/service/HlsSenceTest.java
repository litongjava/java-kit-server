package com.litongjava.linux.service;

import java.io.File;

import org.junit.Test;

import com.litongjava.media.NativeMedia;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HlsSenceTest {

  @Test
  public void testSession() {

    // 配置测试用路径，请根据实际情况修改
    String playlistUrl = "./data/hls/test/main.m3u8";
    String tsPattern = "./data/hls/test/segment_video_%03d.ts";
    int startNumber = 0;
    int segmentDuration = 10; // 每个分段时长（秒）

    long sessionPtr = NativeMedia.initPersistentHls(playlistUrl, tsPattern, startNumber, segmentDuration);
    System.out.println("Session pointer: " + sessionPtr);
    String folderPath = "C:\\Users\\Administrator\\Downloads";
    File folderFile = new File(folderPath);
    File[] listFiles = folderFile.listFiles();
    for (int i = 0; i < listFiles.length; i++) {
      log.info("filename:{}", listFiles[i].getName());
      if (listFiles[i].getName().endsWith(".mp4")) {
        System.out.println(NativeMedia.appendVideoSegmentToHls(sessionPtr, listFiles[i].getAbsolutePath()));

      }
    }

    // 结束会话
    System.out.println(NativeMedia.finishPersistentHls(sessionPtr, playlistUrl));
  }
}
