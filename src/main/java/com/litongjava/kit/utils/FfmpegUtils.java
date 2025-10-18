package com.litongjava.kit.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.commandline.ProcessUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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
  public static ProcessResult m3u82Mp4(String inputFile, String outputFile) throws IOException, InterruptedException {

    List<String> command = new ArrayList<>();
    command.add("ffmpeg");
    command.add("-protocol_whitelist");
    command.add("file,http,https,tcp,tls");
    command.add("-i");
    command.add(inputFile);
    command.add("-c");
    command.add("copy");
    command.add(outputFile);

    ProcessBuilder pb = new ProcessBuilder(command);

    long id = SnowflakeIdUtils.id();
    log.info("id:{} cmd:{}", id, String.join(" ", command));
    File file = new File(id + "");
    ProcessResult result = ProcessUtils.execute(file, id, pb, 3 * 60);
    return result;
  }
}
