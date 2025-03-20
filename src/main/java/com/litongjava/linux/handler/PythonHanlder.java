package com.litongjava.linux.handler;

import com.litongjava.linux.utils.PythonInterpreterUtils;
import com.litongjava.linux.vo.PythonResult;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PythonHanlder {

  public HttpResponse index(HttpRequest request) {
    String code = request.getBodyString();
    HttpResponse response = TioRequestContext.getResponse();

    try {
      PythonResult executeScript = PythonInterpreterUtils.executeCode(code);
      response.setJson(executeScript);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      response.setStatus(500);
      response.setString(e.getMessage());
    }
    return response;
  }

}
