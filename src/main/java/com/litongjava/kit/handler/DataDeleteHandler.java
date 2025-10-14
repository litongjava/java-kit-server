package com.litongjava.kit.handler;

import java.io.File;

import com.litongjava.model.body.RespBodyVo;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.util.CORSUtils;

public class DataDeleteHandler implements HttpRequestHandler {

  @Override
  public HttpResponse handle(HttpRequest httpRequest) throws Exception {

    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    String path = httpRequest.getParam("path");
    File file = new File("." + File.separator + path);
    if (!file.exists()) {
      response.setStatus(404);
      return response;
    }

    boolean delete = file.delete();
    if (delete) {
      return response.body(RespBodyVo.ok());
    } else {
      return response.body(RespBodyVo.fail());
    }
  }
}
