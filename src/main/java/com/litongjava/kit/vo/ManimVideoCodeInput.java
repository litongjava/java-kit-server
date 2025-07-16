package com.litongjava.kit.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain=true)
public class ManimVideoCodeInput {
  private Long id;
  private String code;
  private String quality;
  private int timeout;
  private Boolean stream;
  private Long sessionPrt;
  private String m3u8Path;
}
