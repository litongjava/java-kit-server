package com.litongjava.linux.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.linux.ProcessResult;
import com.litongjava.linux.service.ManimService;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimHanlder {
  ManimService manimService = Aop.get(ManimService.class);

  public HttpResponse index(HttpRequest request) {
    ChannelContext channelContext = request.getChannelContext();
    Boolean stream = request.getBoolean("stream");
    if (stream == null) {
      stream = false;
    }
    String code = request.getBodyString();
    log.info("code:{}", code);
    HttpResponse response = TioRequestContext.getResponse();

    if (stream) {
      response.addServerSentEventsHeader();
      Tio.send(channelContext, response);
      response.setSend(false);
    }
    try {
      ProcessResult executeScript = manimService.executeCode(code, stream, channelContext);
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
