package com.litongjava.linux.handler;

import com.litongjava.linux.utils.PythonInterpreterUtils;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.utils.commandline.ProcessResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PythonHanlder {

  public HttpResponse index(HttpRequest request) {
    String code = request.getBodyString();
    log.info("code:{}", code);
    HttpResponse response = TioRequestContext.getResponse();

    try {
      ProcessResult executeScript = PythonInterpreterUtils.executeCode(code);
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
