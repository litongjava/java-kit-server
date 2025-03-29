package com.litongjava.linux.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.linux.ProcessResult;
import com.litongjava.linux.service.ManimService;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimHanlder {
  ManimService manimService = Aop.get(ManimService.class);

  public HttpResponse index(HttpRequest request) {
    String code = request.getBodyString();
    log.info("code:{}", code);
    HttpResponse response = TioRequestContext.getResponse();
    try {
      ProcessResult executeScript = manimService.executeCode(code);
      executeScript.setExecuteCode(code);
      response.setJson(executeScript);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      response.setStatus(500);
      response.setString(e.getMessage());
    }
    return response;
  }

}
