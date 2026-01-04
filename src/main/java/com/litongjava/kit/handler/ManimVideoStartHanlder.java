package com.litongjava.kit.handler;

import java.io.File;
import java.io.IOException;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.kit.service.HlsSessionService;
import com.litongjava.kit.utils.FolderUtils;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimVideoStartHanlder implements HttpRequestHandler {

  /**
   * 
   */
  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);
    String sessionIdStr = request.getString("session_id");
    Long sessionId = null;
    if (sessionIdStr != null) {
      try {
        sessionId = Long.valueOf(sessionIdStr);
      } catch (Exception e) {
        log.error("Failed to parse value {}", sessionIdStr);
        sessionId = SnowflakeIdUtils.id();
      }

    } else {
      sessionId = SnowflakeIdUtils.id();
    }

    String subPath = FolderUtils.hls(sessionId);
    new File(subPath).mkdirs();
    String name = "main.m3u8";
    String m3u8Path = subPath + File.separator + name;
    try {
      new File(m3u8Path).createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
    }

    String tsPattern = subPath +File.separator +"c_%03d.ts";
    int startNumber = 0;
    int segmentDuration = 2; // 每个分段时长（秒）
    Aop.get(HlsSessionService.class).initPersistentHls(sessionId, m3u8Path, tsPattern, startNumber, segmentDuration);
    ProcessResult processResult = new ProcessResult();
    processResult.setSessionId(sessionId);
    String httpM3u8Path = FolderUtils.httpM3u8(sessionId, name);
    processResult.setHlsUrl(httpM3u8Path);
    processResult.setOutput(httpM3u8Path);

    response.setJson(processResult);
    return response;
  }

}
