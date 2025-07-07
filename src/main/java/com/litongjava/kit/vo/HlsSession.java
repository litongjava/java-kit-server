package com.litongjava.kit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HlsSession {
  private Long sessionId;
  private String playFilePath;
  private boolean finished;

  public HlsSession(Long sessionId, String playFilePath) {
    this.sessionId = sessionId;
    this.playFilePath = playFilePath;
    this.finished = false;
  }

}