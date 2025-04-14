package com.litongjava.linux.handler;

import java.io.File;
import java.io.IOException;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.linux.ProcessResult;
import com.litongjava.linux.service.ManimCodeExecuteService;
import com.litongjava.media.NativeMedia;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.hutool.FilenameUtils;
import com.litongjava.tio.utils.json.JsonUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimHanlder {
  ManimCodeExecuteService manimService = Aop.get(ManimCodeExecuteService.class);

  public HttpResponse start(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);
    long sessionId = SnowflakeIdUtils.id();
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
    int segmentDuration = 5; //每个分段时长（秒）
    long initPersistentHls = NativeMedia.initPersistentHls(m3u8Path, tsPattern, startNumber, segmentDuration);

    ProcessResult processResult = new ProcessResult();
    processResult.setSessionId(sessionId);
    processResult.setSessionIdPrt(initPersistentHls);
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
    log.info("session_prt:{},m3u8Path:{}", session_prt, m3u8Path, videos);
    File file = new File(m3u8Path);
    if (file.exists()) {
      log.info("finishPersistentHls");
      NativeMedia.finishPersistentHls(session_prt, m3u8Path);
    } else {
      log.info("freeHlsSession");
      NativeMedia.freeHlsSession(session_prt);
    }

    if (videos != null) {
      String subPath = FilenameUtils.getSubPath(m3u8Path);
      String outputPath = subPath + "/main.mp4";
      String[] split = videos.split(",");
      String[] mp4FileList = new String[split.length];
      for (int i = 0; i < split.length; i++) {
        String string = "." + split[i].replace(".m3u8", ".mp4");
        mp4FileList[i] = string;
      }
      log.info("merge:{}", JsonUtils.toJson(mp4FileList));
      boolean merged = NativeMedia.merge(mp4FileList, outputPath);
      log.info("merged:{}", merged);
      if (merged) {
        double videoLength = NativeMedia.getVideoLength(outputPath);
        processResult.setViode_length(videoLength);
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
    log.info("{},{}", session_prt, m3u8Path);
    if (stream == null) {
      stream = false;
    }
    String code = request.getBodyString();

    if (stream) {
      response.addServerSentEventsHeader();
      Tio.send(channelContext, response);
      response.setSend(false);
    }
    try {
      ProcessResult executeScript = manimService.executeCode(code, stream, session_prt, m3u8Path, channelContext);
      if (executeScript != null) {
        response.setJson(executeScript);
      }

    } catch (Exception e) {
      log.error(e.getMessage(), e);
      response.setStatus(500);
      response.setString(e.getMessage());
    }
    return response;
  }

}
