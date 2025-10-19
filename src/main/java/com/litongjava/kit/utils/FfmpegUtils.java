package com.litongjava.kit.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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

  /** 方案A：将 m3u8 转换为 mp4（先补 #EXT-X-ENDLIST，防止卡住） */
  public static ProcessResult m3u82Mp4(String inputFile, String outputFile) throws IOException, InterruptedException {
    File m3u8 = new File(inputFile);
    if (!m3u8.isFile()) {
      throw new IOException("m3u8 not found: " + m3u8.getAbsolutePath());
    }

    ensureEndlist(m3u8);

    // 统一使用绝对路径，避免工作目录差异
    String inputAbs = m3u8.getAbsolutePath();
    String outputAbs = new File(outputFile).getAbsolutePath();

    List<String> command = new ArrayList<>();
    command.add("ffmpeg");
    command.add("-v");
    command.add("error");
    command.add("-stats");
    command.add("-protocol_whitelist");
    command.add("file,http,https,tcp,tls");
    command.add("-i");
    command.add(inputAbs);
    command.add("-c");
    command.add("copy");
    command.add("-bsf:a");
    command.add("aac_adtstoasc");
    command.add("-movflags");
    command.add("+faststart");
    command.add("-y");
    command.add(outputAbs);

    ProcessBuilder pb = new ProcessBuilder(command);

    long id = SnowflakeIdUtils.id();
    log.info("id:{} cmd:{}", id, String.join(" ", command));
    File logDir = new File(LOG_FOLDER, String.valueOf(id));
    return ProcessUtils.execute(logDir, id + "", pb, 10 * 60);
  }

  /** 检测 MP4 是否“可播放”：有视频流 && 时长 > 0 */
  public static boolean isMp4Playable(File mp4) throws IOException, InterruptedException {
    if (mp4 == null || !mp4.isFile())
      return false;
    if (mp4.length() < 64 * 1024)
      return false;

    // 1) 用 ffprobe 读取时长
    List<String> durCmd = Arrays.asList("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of",
        "default=nw=1:nk=1", mp4.getAbsolutePath());
    String durOut = execAndGetStdout(durCmd, 15);
    double duration = parsePositiveDouble(durOut.trim());
    if (!(duration > 0.1))
      return false;

    // 2) 是否存在视频流
    List<String> vidCmd = Arrays.asList("ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
        "stream=codec_type", "-of", "csv=p=0", mp4.getAbsolutePath());
    String vidOut = execAndGetStdout(vidCmd, 15);
    String t = vidOut.trim().toLowerCase(Locale.ROOT);
    return t.contains("video");
  }

  private static String execAndGetStdout(List<String> command, int timeoutSeconds)
      throws IOException, InterruptedException {
    long id = SnowflakeIdUtils.id();
    File logDir = new File(LOG_FOLDER, String.valueOf(id));
    ProcessBuilder pb = new ProcessBuilder(command);
    ProcessResult pr = ProcessUtils.execute(logDir, id + "", pb, timeoutSeconds);
    if (pr.getExitCode() != 0) {
      // 将 stderr 合并到异常信息中，方便排查
      throw new IOException("Command failed, exit=" + pr.getExitCode() + ", cmd=" + String.join(" ", command)
          + ", stderr=" + safe(pr.getStdErr()));
    }
    return pr.getStdOut() == null ? "" : pr.getStdOut();
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  private static double parsePositiveDouble(String s) {
    try {
      double v = Double.parseDouble(s);
      return v > 0 ? v : -1;
    } catch (Exception e) {
      return -1;
    }
  }

  /** 若缺少 #EXT-X-ENDLIST，则在文件末尾追加，避免被当直播等待 */
  private static void ensureEndlist(File m3u8) throws IOException {
    byte[] contentBytes = Files.readAllBytes(m3u8.toPath());
    String content = new String(contentBytes, StandardCharsets.UTF_8);
    if (!content.contains("#EXT-X-ENDLIST")) {
      String append = content.endsWith("\n") ? "#EXT-X-ENDLIST\n" : "\n#EXT-X-ENDLIST\n";
      Files.write(m3u8.toPath(), append.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
      log.info("Appended #EXT-X-ENDLIST to {}", m3u8.getAbsolutePath());
    }
  }
}
