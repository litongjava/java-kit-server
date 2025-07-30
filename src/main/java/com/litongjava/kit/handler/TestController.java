package com.litongjava.kit.handler;

import com.litongjava.annotation.RequestPath;
import com.litongjava.media.NativeMedia;
import com.litongjava.model.body.RespBodyVo;

@RequestPath("/test")
public class TestController {

  public RespBodyVo toMp3ForSilence() {
    String inputMp4Path = "videos/01/main.mp4";
    String mp3Path = NativeMedia.toMp3ForSilence(inputMp4Path, 0.72);
    return RespBodyVo.ok(mp3Path);
  }

}
