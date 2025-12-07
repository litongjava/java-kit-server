package com.litongjava.kit.handler;

import java.io.File;
import java.io.IOException;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.kit.service.ManimVideoCodeExecuteService;
import com.litongjava.kit.vo.VideoCodeInput;
import com.litongjava.media.NativeMedia;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimVideoHanlder {
  ManimVideoCodeExecuteService manimService = Aop.get(ManimVideoCodeExecuteService.class);

  public HttpResponse start(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);
    String sessionIdStr = request.getString("session-id");
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

    String subPath = "./data/session/" + sessionId;
    new File(subPath).mkdirs();
    String m3u8Path = subPath + "/main.m3u8";
    try {
      new File(m3u8Path).createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
    }

    String tsPattern = subPath + "/segment_video_%03d.ts";
    int startNumber = 0;
    int segmentDuration = 2; // 每个分段时长（秒）
    long hlsPtr = NativeMedia.initPersistentHls(m3u8Path, tsPattern, startNumber, segmentDuration);

    ProcessResult processResult = new ProcessResult();
    processResult.setSessionId(sessionId);
    processResult.setSessionIdPrt(hlsPtr);
    processResult.setOutput(m3u8Path.replace("./", "/"));

    response.setJson(processResult);
    return response;
  }

  public HttpResponse index(HttpRequest request) {

    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    ChannelContext channelContext = request.getChannelContext();
    Boolean stream = request.getBoolean("stream");
    Long session_prt = request.getLong("session_prt");
    String m3u8Path = request.getString("m3u8_path");

    String code_timeout = request.getHeader("code-timeout");
    Integer timeout = null;
    if (code_timeout != null) {
      timeout = Integer.valueOf(code_timeout);
    } else {
      timeout = 590;
    }

    String sessionIdStr = request.getHeader("session-id");

    Long sessionId = null;
    if (sessionIdStr != null) {
      sessionId = Long.valueOf(sessionIdStr);
    } else {
      sessionId = SnowflakeIdUtils.id();
    }

    String code_id = request.getHeader("code-id");

    Long id = null;
    if (code_id != null) {
      id = Long.valueOf(code_id);
    } else {
      id = SnowflakeIdUtils.id();
    }

    String quality = request.getHeader("quality");
    if (quality == null) {
      quality = "l";
    }

    log.info("session_id:{},session_prt={},m3u8Path={},code_id={},code_timeout={},quality={}", sessionId, session_prt,
        m3u8Path, code_id, code_timeout, quality);
    if (stream == null) {
      stream = false;
    }
    String code = null;
    UploadFile uploadFile = request.getUploadFile("code");
    if (uploadFile != null) {
      code = new String(uploadFile.getData());
    } else {
      code = request.getBodyString();
    }

    String figure = null;
    UploadFile figureFile = request.getUploadFile("figure");
    if (figureFile != null) {
      figure = new String(figureFile.getData());
    }

    if (stream) {
      response.addServerSentEventsHeader();
      Tio.bSend(channelContext, response);
      response.setSend(false);
    }
    VideoCodeInput manimVideoCodeInput = new VideoCodeInput(sessionId, id, code, quality, timeout, stream, session_prt,
        m3u8Path, figure);
    try {
      ProcessResult executeScript = manimService.executeCode(manimVideoCodeInput, channelContext);
      if (executeScript != null) {
        response.setJson(executeScript);
      }

    } catch (Exception e) {
      log.error(e.getMessage(), e);
      response.setStatus(500);
      response.body(e.getMessage());
    }
    return response;
  }

}
