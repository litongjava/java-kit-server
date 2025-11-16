package com.litongjava.kit.handler;

import java.util.concurrent.locks.Lock;

import com.google.common.util.concurrent.Striped;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.kit.service.MotionCanvasCodeExecuteService;
import com.litongjava.kit.vo.VideoCodeInput;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MotionCanvasHandler implements HttpRequestHandler {
  private MotionCanvasCodeExecuteService srv = Aop.get(MotionCanvasCodeExecuteService.class);
  private static final Striped<Lock> locks = Striped.lock(1024);

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {

    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    ChannelContext channelContext = request.getChannelContext();

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

    String code_name = request.getHeader("code-name");

    if (code_name == null) {
      code_name = "Scene01";
    }
    log.info("session_id:{},code_id={},code_timeout={},code_name={}", sessionId, code_id, code_timeout, code_name);

    String code = null;
    UploadFile uploadFile = request.getUploadFile("code");
    if (uploadFile != null) {
      code = new String(uploadFile.getData());
    } else {
      code = request.getBodyString();
    }

    VideoCodeInput manimVideoCodeInput = new VideoCodeInput(sessionId, id, code_name, code, timeout);
    // 同一时间,只能写入一个sessionId
    Lock lock = locks.get(sessionId);
    lock.lock();
    try {
      ProcessResult executeScript = srv.executeCode(manimVideoCodeInput, channelContext);
      if (executeScript != null) {
        response.setJson(executeScript);
      }

    } catch (Exception e) {
      log.error(e.getMessage(), e);
      response.setStatus(500);
      response.body(e.getMessage());
    } finally {
      lock.unlock();
    }
    return response;
  }
}
