package com.litongjava.kit.handler;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.utils.IpBlacklistUtils;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BlackIpHandler implements HttpRequestHandler {

  @Override
  public HttpResponse handle(HttpRequest httpRequest) throws Exception {
    // 获取请求的真实IP地址
    String realIp = httpRequest.getParam("ip");
    // 简单到极致，只需要一行代码 将IP地址添加到黑名单中
    IpBlacklistUtils.add(realIp);
    log.info("blackIp:{}", realIp);

    // 返回响应
    return TioRequestContext.getResponse();
  }
}
