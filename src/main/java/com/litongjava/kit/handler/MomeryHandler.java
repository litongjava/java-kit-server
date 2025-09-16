package com.litongjava.kit.handler;

import com.litongjava.enhance.buffer.BufferMomeryInfo;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.pool.BufferPoolUtils;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.utils.json.FastJson2Utils;

public class MomeryHandler {

  public HttpResponse index(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    BufferMomeryInfo bufferMomeryInfo = BufferPoolUtils.getBufferMomeryInfo();
    String ok = FastJson2Utils.toJson(bufferMomeryInfo);
    return response.body(ok);
  }
}
