package com.litongjava.uni.handler;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.Resps;
import com.litongjava.uni.services.ManimTTSService;

public class ManimTTSHandler {
  ManimTTSService manimTTSService = Aop.get(ManimTTSService.class);

  public HttpResponse index(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    String input = request.getParam("input");
    String platform = request.getParam("platform");
    String voice_id = request.getParam("voice_id");

    byte[] audio = manimTTSService.tts(input, platform, voice_id);
    return Resps.bytesWithContentType(response, audio, "audio/mp3");
  }
}