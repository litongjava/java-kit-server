package com.litongjava.kit.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.kit.service.ManimVideoCodeExecuteService;
import com.litongjava.kit.store.HlsSessionStore;
import com.litongjava.kit.vo.HlsSession;
import com.litongjava.kit.vo.VideoCodeInput;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimVideoRunHanlder implements HttpRequestHandler {
  ManimVideoCodeExecuteService manimService = Aop.get(ManimVideoCodeExecuteService.class);

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    ChannelContext channelContext = request.getChannelContext();
    Boolean stream = request.getBoolean("stream");
    String sessionIdStr = request.getParam("session_id");
    String code_id = request.getParam("code_id");
    String code_timeout = request.getParam("code_timeout");
    String storagePlatform = request.getParam("storage_platform");
    String quality = request.getParam("quality");

    Integer timeout = null;
    if (code_timeout != null) {
      timeout = Integer.valueOf(code_timeout);
    } else {
      timeout = 590;
    }

    Long sessionId = null;
    if (sessionIdStr != null) {
      sessionId = Long.valueOf(sessionIdStr);
    } else {
      sessionId = SnowflakeIdUtils.id();
    }

    Long id = null;
    if (code_id != null) {
      id = Long.valueOf(code_id);
    } else {
      id = SnowflakeIdUtils.id();
    }

    if (quality == null) {
      quality = "l";
    }

    HlsSession hlsSession = HlsSessionStore.get(sessionId);

    String m3u8Path = null;
    Long session_prt = null;
    if (hlsSession != null) {
      m3u8Path = hlsSession.getHls();
      session_prt = hlsSession.getPrt();
    }

    log.info("session_id:{},session_prt:{},m3u8Path={},code_id={},code_timeout={},quality={}", sessionId, session_prt, m3u8Path, code_id,
        code_timeout, quality);
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

    VideoCodeInput manimVideoCodeInput = new VideoCodeInput(sessionId, id, code, quality, timeout, stream, session_prt, m3u8Path, figure,
        storagePlatform);

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
