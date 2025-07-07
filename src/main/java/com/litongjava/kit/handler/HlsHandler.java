package com.litongjava.kit.handler;

import com.alibaba.fastjson2.JSONObject;
import com.litongjava.kit.service.HlsService;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.json.FastJson2Utils;

/**
 * HLS 接口处理 Handler
 */
public class HlsHandler {

  private HlsService hlsService = new HlsService();

  /**
   * 接口：POST /hls/start
   * 会话初始化，返回播放列表 URL
   */
  public HttpResponse start(HttpRequest httpRequest) {
    HttpResponse response = TioRequestContext.getResponse();
    String bodyString = httpRequest.getBodyString();
    JSONObject requestBody = FastJson2Utils.parseObject(bodyString);
    Long sessionId = requestBody.getLong("sessionId");
    Long timestamp = requestBody.getLong("timestamp");
    RespBodyVo bodyVo = hlsService.startSession(sessionId, timestamp);
    return response.setJson(bodyVo);
  }

  /**
   * 接口：POST /hls/upload
   * 上传 MP4 场景文件，multipart/form-data 格式（此处简单用 JSON 模拟）
   */
  public HttpResponse upload(HttpRequest httpRequest) {
    HttpResponse response = TioRequestContext.getResponse();
    UploadFile uploadFile = httpRequest.getUploadFile("file");
    Long sessionId = httpRequest.getLong("sessionId");
    // 模拟文件数据
    RespBodyVo bodyVo = hlsService.convert(sessionId, uploadFile);
    return response.setJson(bodyVo);
  }

  /**
   * 接口：GET /hls/stream
   * 返回当前播放列表 URL
   */
  public HttpResponse stream(HttpRequest httpRequest) {
    HttpResponse response = TioRequestContext.getResponse();
    String sessionIdStr = httpRequest.getParameter("sessionId");
    Long sessionId;
    try {
      sessionId = Long.parseLong(sessionIdStr);
    } catch (Exception e) {
      return response.setJson(RespBodyVo.fail("Invalid sessionId"));
    }
    RespBodyVo bodyVo = hlsService.getStreamUrl(sessionId);
    return response.setJson(bodyVo);
  }

  /**
   * 接口：GET /hls/status
   * 查询当前处理状态
   */
  public HttpResponse status(HttpRequest httpRequest) {
    HttpResponse response = TioRequestContext.getResponse();
    String sessionIdStr = httpRequest.getParameter("sessionId");
    Long sessionId;
    try {
      sessionId = Long.parseLong(sessionIdStr);
    } catch (Exception e) {
      return response.setJson(RespBodyVo.fail("Invalid sessionId"));
    }
    RespBodyVo bodyVo = hlsService.getStatus(sessionId);
    return response.setJson(bodyVo);
  }

  /**
   * 接口：POST /hls/finish
   * 会话结束，追加 EXT ENDLIST 标签
   */
  public HttpResponse finish(HttpRequest httpRequest) {
    HttpResponse response = TioRequestContext.getResponse();
    String bodyString = httpRequest.getBodyString();
    JSONObject requestBody = FastJson2Utils.parseObject(bodyString);
    // 支持同时传入 "session_id" 或 "sessionId"
    Long sessionId = requestBody.getLong("session_id");
    if (sessionId == null) {
      sessionId = requestBody.getLong("sessionId");
    }
    Long finishTime = requestBody.getLong("finish_time");
    if (finishTime == null) {
      finishTime = SystemTimer.currTime;
    }
    RespBodyVo bodyVo = hlsService.finishSession(sessionId, finishTime);
    return response.setJson(bodyVo);
  }
}
