package com.litongjava.linux.service;

import java.io.File;

import org.junit.Test;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

public class HlsServiceTest {
  @Test
  public void test() {
    HlsService hlsService = Aop.get(HlsService.class);
    long sessionId = SnowflakeIdUtils.id();

    hlsService.startSession(sessionId, System.currentTimeMillis());

    String filePath = "G:\\video\\CombinedScene.mp4";
    File file = new File(filePath);
    byte[] bytes = FileUtil.readBytes(file);
    UploadFile uploadFile = new UploadFile(file.getName(), bytes);
    hlsService.uploadScene(sessionId, 1, uploadFile);

    hlsService.finishSession(sessionId, System.currentTimeMillis());
  }

}
