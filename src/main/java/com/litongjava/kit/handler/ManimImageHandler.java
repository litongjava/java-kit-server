package com.litongjava.kit.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.kit.service.ManimImageService;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.commandline.ProcessResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimImageHandler {
  ManimImageService manimService = Aop.get(ManimImageService.class);

  public HttpResponse index(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    String code = request.getBodyString();

    try {
      ProcessResult executeScript = manimService.executeCode(code);
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
