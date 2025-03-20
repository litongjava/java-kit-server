package com.litongjava.linux.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PythonResult {
  private int exitCode;
  private String stdOut;
  private String stdErr;
  private String image;
}
