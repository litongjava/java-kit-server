package com.litongjava.kit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class VideoCodeInput {

  private Long sessionId;
  private Long taskId;
  private String taskName;
  private String code;
  private String quality;
  private Integer timeout;
  // 图表的json格式
  private Boolean stream;
  private Long sessionPrt;
  private String m3u8Path;
  private String figure;
  private String storagePlatform;

  public VideoCodeInput(Long sessionId, Long codeId, String code_name, String code, Integer code_timeout) {
    this.sessionId = sessionId;
    this.taskId = codeId;
    this.taskName = code_name;
    this.code = code;
    this.timeout = code_timeout;

  }

  public VideoCodeInput(Long sessionId, Long id, String code, String quality, Integer timeout, Boolean stream, Long session_prt,
      String m3u8Path, String figure, String storagePlatform) {
    this.sessionId = sessionId;
    this.taskId = id;
    this.code = code;
    this.quality = quality;
    this.timeout = timeout;
    this.stream = stream;
    this.sessionPrt = session_prt;
    this.m3u8Path = m3u8Path;
    this.figure = figure;
    this.storagePlatform = storagePlatform;
  }
}
