package com.litongjava.kit.handler;

import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.utils.HttpIpUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PingHandler {
  public HttpResponse ping(HttpRequest request) {
    log.info("ping from :{}", HttpIpUtils.getRealIp(request));
    HttpResponse response = TioRequestContext.getResponse();
    response.body("pong");
    return response;
  }
}
