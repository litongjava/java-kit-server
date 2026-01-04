package com.litongjava.kit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HlsSession {
  private Long sessionId;
  private Long prt;
  private String hls;
  private boolean finished;

  public HlsSession(Long sessionId, String playFilePath) {
    this.sessionId = sessionId;
    this.hls = playFilePath;
    this.finished = false;
  }

  public HlsSession(String hls, Long ptr) {
    this.hls = hls;
    this.prt = ptr;
  }

}