package com.litongjava.kit.handler;

import java.io.File;
import java.io.IOException;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.kit.service.ManimVideoCodeExecuteService;
import com.litongjava.kit.vo.ManimVideoCodeInput;
import com.litongjava.media.NativeMedia;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.hutool.FilenameUtils;
import com.litongjava.tio.utils.json.JsonUtils;
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

  public HttpResponse finish(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    Long session_prt = request.getLong("session_prt");
    ProcessResult processResult = new ProcessResult();
    if (session_prt == null) {
      processResult.setStdErr("m3u8_path can not be empty");
      return response.setJson(processResult);
    }
    String m3u8Path = request.getString("m3u8_path");
    if (m3u8Path == null) {
      processResult.setStdErr("m3u8_path can not be empty");
      return response.setJson(processResult);
    } else {
      m3u8Path = "." + m3u8Path;
    }
    String videos = request.getString("videos");
    log.info("session_prt:{},m3u8Path:{},videos:{}", session_prt, m3u8Path, videos);
    File file = new File(m3u8Path);
    if (file.exists()) {
      log.info("finishPersistentHls:{}", session_prt);
      NativeMedia.finishPersistentHls(session_prt, m3u8Path);
    } else {
      log.info("freeHlsSession:{}", session_prt);
      NativeMedia.freeHlsSession(session_prt);
    }

    if (videos != null) {
      String subPath = FilenameUtils.getSubPath(m3u8Path);
      String outputMp4Path = subPath + "/main.mp4";
      String[] split = videos.split(",");
      String[] mp4FileList = new String[split.length];
      String[] wavFileList = new String[split.length];
      for (int i = 0; i < split.length; i++) {
        mp4FileList[i] = "." + split[i].replace(".m3u8", ".mp4");
        wavFileList[i] = "." + split[i].replace(".m3u8", ".wav");
      }
      log.info("merge:{}", JsonUtils.toJson(mp4FileList));
      boolean merged = NativeMedia.merge(mp4FileList, outputMp4Path);
      log.info("merged:{}", merged);
      if (merged) {
        double videoLength = NativeMedia.getVideoLength(outputMp4Path);
        log.info("video_length:{} {}", outputMp4Path, videoLength);
        processResult.setVideo_length(videoLength);
        double delta = videoLength - 120d;
        String mp3Path = null;
        if (delta > 0) {
          double insertion_silence_duration = delta / 100;
          mp3Path = NativeMedia.toMp3ForSilence(outputMp4Path, insertion_silence_duration);
        } else {
          mp3Path = NativeMedia.toMp3(outputMp4Path);
        }
        processResult.setAudio(mp3Path);
        log.info("mp3:{}", mp3Path);
      }
    }

    return response.setJson(processResult);
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
      id = sessionId;
    }

    String quality = request.getHeader("quality");
    if (quality == null) {
      quality = "l";
    }

    log.info("session_prt={},m3u8Path={},code_id={},code_timeout={},quality={}", session_prt, m3u8Path, code_id,
        code_timeout, quality);
    if (stream == null) {
      stream = false;
    }
    String code = request.getBodyString();

    if (stream) {
      response.addServerSentEventsHeader();
      Tio.bSend(channelContext, response);
      response.setSend(false);
    }
    ManimVideoCodeInput manimVideoCodeInput = new ManimVideoCodeInput(sessionId, id, code, quality, timeout, stream,
        session_prt, m3u8Path);
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
