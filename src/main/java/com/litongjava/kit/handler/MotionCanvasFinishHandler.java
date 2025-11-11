package com.litongjava.kit.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.kit.service.MotionCanvasCodeExecuteService;
import com.litongjava.linux.SessionFinishRequest;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MotionCanvasFinishHandler implements HttpRequestHandler {
  private MotionCanvasCodeExecuteService srv = Aop.get(MotionCanvasCodeExecuteService.class);

  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {

    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    String bodyString = request.getBodyString();

    if (bodyString == null) {
      return response.fail("body can not be empty");
    }
    SessionFinishRequest modelRequest = JsonUtils.parse(bodyString, SessionFinishRequest.class);

    try {
      ProcessResult executeScript = srv.finish(modelRequest);
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
