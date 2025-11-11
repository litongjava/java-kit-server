package com.litongjava.kit.service;

import org.junit.Test;

import com.litongjava.linux.SessionFinishRequest;
import com.litongjava.tio.utils.json.JsonUtils;

public class MotionCanvasCodeExecuteServiceTest {

  @Test
  public void finish() {
    Long sessionId = 578029478896693248L;
    String[] sceneNames = { "Scene1", "Scene2", "Scene3", "Scene4", "Scene5", "Scene6" };
    SessionFinishRequest request = new SessionFinishRequest(sessionId, sceneNames);
    System.out.println(JsonUtils.toJson(request));
//    try {
//      Aop.get(MotionCanvasCodeExecuteService.class).finish(request);
//    } catch (IOException e) {
//      e.printStackTrace();
//    } catch (InterruptedException e) {
//      e.printStackTrace();
//    }
  }
}
