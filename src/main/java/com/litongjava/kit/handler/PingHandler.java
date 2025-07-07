package com.litongjava.kit.handler;

import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;

public class PingHandler {
  public HttpResponse ping(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    response.body("pong");
    return response;
  }
}
