package com.litongjava.linux.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.litongjava.tio.utils.commandline.CommandLineResult;

public class CmdInterpreterUtils {

  public static CommandLineResult executeCmd(String cmd) {
    CommandLineResult result = new CommandLineResult();

    try {
      ProcessBuilder processBuilder;
      String osName = System.getProperty("os.name").toLowerCase();
      // 根据不同的操作系统使用合适的命令解释器
      if (osName.contains("windows")) {
        processBuilder = new ProcessBuilder("cmd.exe", "/c", cmd);
      } else {
        processBuilder = new ProcessBuilder("bash", "-c", cmd);
      }

      Process process = processBuilder.start();

      // 读取标准输出
      BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
      StringBuilder outputBuilder = new StringBuilder();
      String line;
      while ((line = stdInput.readLine()) != null) {
        outputBuilder.append(line).append("\n");
      }

      // 读取错误输出
      BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
      StringBuilder errorBuilder = new StringBuilder();
      while ((line = stdError.readLine()) != null) {
        errorBuilder.append(line).append("\n");
      }

      // 等待进程结束
      int exitCode = process.waitFor();

      result.setExitCode(exitCode);
      result.setStdOut(outputBuilder.toString());
      result.setStdErr(errorBuilder.toString());

    } catch (IOException | InterruptedException e) {
      result.setStdErr(e.getMessage());
      result.setExitCode(-1);
    }
    return result;
  }
}
