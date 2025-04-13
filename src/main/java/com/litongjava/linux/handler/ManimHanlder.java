package com.litongjava.linux.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.linux.ProcessResult;
import com.litongjava.linux.service.ManimService;
import com.litongjava.media.NativeMedia;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.hutool.FilenameUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimHanlder {
  ManimService manimService = Aop.get(ManimService.class);

  public HttpResponse start(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    String m3u8Path = request.getString("m3u8_path");
    String subPath = FilenameUtils.getSubPath(m3u8Path);
    String tsPattern = subPath + "/segment_video_%03d.ts";
    int startNumber = 0;
    int segmentDuration = 10; // 每个分段时长（秒）
    long initPersistentHls = NativeMedia.initPersistentHls(null, tsPattern, startNumber, segmentDuration);

    ProcessResult processResult = new ProcessResult();
    processResult.setSessionId(initPersistentHls);
    response.setJson(processResult);
    return response;
  }

  public HttpResponse finish(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    Long sessionId = request.getLong("session_id");
    String m3u8Path = request.getString("m3u8_path");
    NativeMedia.finishPersistentHls(sessionId, m3u8Path);

    ProcessResult processResult = new ProcessResult();
    String videos = request.getString("videos");
    if (videos != null) {
      String[] split = videos.split(",");
      String subPath = FilenameUtils.getSubPath(m3u8Path);
      String outputPath = subPath + "/main.mp4";
      boolean merged = NativeMedia.merge(split, outputPath);
      if (merged) {
        double videoLength = NativeMedia.getVideoLength(outputPath);
        processResult.setSessionId(sessionId);
        processResult.setViode_length(videoLength);
      }
    }

    return response;
  }

  public HttpResponse index(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    ChannelContext channelContext = request.getChannelContext();
    Boolean stream = request.getBoolean("stream");
    String m3u8Path = request.getString("m3u8_path");
    Long sessionId = request.getLong("session_id");
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
      ProcessResult executeScript = manimService.executeCode(code, stream, sessionId, m3u8Path, channelContext);
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
