package com.litongjava.kit.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FfmpegUtils {

  public static final String LOG_FOLDER = "ffmpeg_logs";

  static {
    File logDir = new File(LOG_FOLDER);
    if (!logDir.exists()) {
      logDir.mkdirs();
    }
  }

  /**
   * 将 m3u8 转换为 mp4（不加水印）
   * 
   * @param inputFile  输入 m3u8 文件路径
   * @param outputFile 输出 mp4 文件路径
   * @return FFmpeg 执行退出码（0 表示成功）
   */
  public static int m3u82Mp4(String inputFile, String outputFile) throws IOException, InterruptedException {

    List<String> command = new ArrayList<>();
    command.add("ffmpeg");
    command.add("-protocol_whitelist");
    command.add("file,http,https,tcp,tls");
    command.add("-i");
    command.add(inputFile);
    command.add("-c");
    command.add("copy");
    command.add(outputFile);

    System.out.println("cmd: " + String.join(" ", command));

    ProcessBuilder pb = new ProcessBuilder(command);

    // 日志输出
    File stdoutFile = new File(LOG_FOLDER, "ffmpeg_stdout.log");
    File stderrFile = new File(LOG_FOLDER, "ffmpeg_stderr.log");
    pb.redirectOutput(ProcessBuilder.Redirect.to(stdoutFile));
    pb.redirectError(ProcessBuilder.Redirect.to(stderrFile));

    Process process = pb.start();
    return process.waitFor();
  }
}
