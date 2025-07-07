package com.litongjava.kit.handler;

import com.litongjava.aio.BytePacket;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpeedTestHandler {

  /**
   * 测速接口：GET /speed/test?size={size in MB}
   */
  public HttpResponse output(HttpRequest request) {
    HttpResponse httpResponse = TioRequestContext.getResponse();

    // 读取 size 参数（单位 MB）
    Long sizeMb = request.getLong("size");
    if (sizeMb == null || sizeMb < 0) {
      sizeMb = 500L;
    }

    long totalBytes = sizeMb * 1024L * 1024L;
    httpResponse.setContentType("application/octet-stream");
    httpResponse.header("Content-Length", String.valueOf(totalBytes));
    //告诉编码器,已经设置了响应头的Content-Length,不要再计算
    httpResponse.setHasCountContentLength(true);
    Tio.bSend(request.channelContext, httpResponse);

    long chunkBytes = 1 * 1024L * 1024L;
    // 生成指定大小的零字节数组
    byte[] payload = new byte[(int) chunkBytes];

    // 计算字节数
    for (int i = 1; i < sizeMb + 1; i++) {

      BytePacket bytePacket = new BytePacket(payload);
      // 向客户端发送消息
      log.info("send:{},{}", i, chunkBytes);
      Tio.bSend(request.channelContext, bytePacket);
    }

    // 构造响应
    Tio.close(request.channelContext, "close");

    //告诉编码器 这个httpResponse已经发送过了,不要再次发送
    httpResponse.setSend(false);
    return httpResponse;
  }
}
