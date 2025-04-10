package com.litongjava.linux.service;

import java.io.File;

import org.junit.Test;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.media.NativeMedia;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.json.JsonUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HlsServiceTest {
  @Test
  public void test() {
    HlsService hlsService = Aop.get(HlsService.class);
    long sessionId = SnowflakeIdUtils.id();
    String filePath = "G:\\video\\CombinedScene.mp4";
    File file = new File(filePath);
    byte[] bytes = FileUtil.readBytes(file);
    UploadFile uploadFile = new UploadFile(file.getName(), bytes);
    RespBodyVo convert = hlsService.convert(sessionId, uploadFile);
    System.out.println(JsonUtils.toJson(convert));

  }

  @Test
  public void test2() {
    String folderPath = "E:\\code\\java\\project-litongjava\\java-linux\\data\\07\\videos\\1080p30\\partial_movie_files\\CombinedScene";

    HlsService hlsService = Aop.get(HlsService.class);
    long sessionId = SnowflakeIdUtils.id();

    hlsService.startSession(sessionId, System.currentTimeMillis());
    File folderFile = new File(folderPath);
    File[] listFiles = folderFile.listFiles();
    for (int i = 0; i < listFiles.length; i++) {
      log.info("filename:{}", listFiles[i].getName());
      if (listFiles[i].getName().endsWith(".mp4")) {
        byte[] bytes = FileUtil.readBytes(listFiles[i]);
        UploadFile uploadFile = new UploadFile(listFiles[i].getName(), bytes);
        //hlsService.uploadScene(sessionId, (i + 1), uploadFile);
      }
    }
    hlsService.finishSession(sessionId, System.currentTimeMillis());
  }

  @Test
  public void testSession() {

    String folderPath = "E:\\code\\java\\project-litongjava\\java-linux\\data\\07\\videos\\1080p30\\partial_movie_files\\CombinedScene";

    // 配置测试用路径，请根据实际情况修改
    String playlistUrl = "./data/hls/test/playlist_video.m3u8";
    String tsPattern = "./data/hls/test/segment_video_%03d.ts";
    int startNumber = 0;
    int segmentDuration = 10; // 每个分段时长（秒）

    // 初始化持久化 HLS 会话，返回 session 指针
    long sessionPtr = NativeMedia.initPersistentHls(playlistUrl, tsPattern, startNumber, segmentDuration);
    System.out.println("Session pointer: " + sessionPtr);

    File folderFile = new File(folderPath);
    File[] listFiles = folderFile.listFiles();
    for (int i = 0; i < listFiles.length; i++) {
      log.info("filename:{}", listFiles[i].getName());
      if (listFiles[i].getName().endsWith(".mp4")) {
        System.out.println(NativeMedia.appendMp4Segment(sessionPtr, listFiles[i].getAbsolutePath()));
      }
    }

    // 结束会话
    System.out.println(NativeMedia.finishPersistentHls(sessionPtr, playlistUrl));
  }

  @Test
  public void testAudioSession() {

    String folderPath = "E:\\code\\java\\project-litongjava\\java-linux\\data\\07\\audio";

    // 配置测试用路径，请根据实际情况修改
    String playlistUrl = "./data/hls/test/playlist_audio.m3u8";
    String tsPattern = "./data/hls/test/segment_audio_%03d.ts";
    int startNumber = 0;
    int segmentDuration = 10; // 每个分段时长（秒）

    // 初始化持久化 HLS 会话，返回 session 指针
    long sessionPtr = NativeMedia.initPersistentHls(playlistUrl, tsPattern, startNumber, segmentDuration);
    System.out.println("Session pointer: " + sessionPtr);

    File folderFile = new File(folderPath);
    File[] listFiles = folderFile.listFiles();
    for (int i = 0; i < listFiles.length; i++) {
      log.info("filename:{}", listFiles[i].getName());
      if (listFiles[i].getName().endsWith(".mp3")) {
        System.out.println(NativeMedia.appendMp4Segment(sessionPtr, listFiles[i].getAbsolutePath()));
        // 在适当的转场位置插入静音段（例如静音 1 秒）
        System.out.println(NativeMedia.insertSilentSegment(sessionPtr, 1.0));
      }
    }

    // 结束会话
    System.out.println(NativeMedia.finishPersistentHls(sessionPtr, playlistUrl));
  }
}
