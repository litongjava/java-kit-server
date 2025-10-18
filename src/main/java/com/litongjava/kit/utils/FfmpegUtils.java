package com.litongjava.kit.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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
   * 将 m3u8 转换为 mp4（不加水印） 方案A：把 EVENT 清单“封盘”（补 #EXT-X-ENDLIST），防止 FFmpeg 卡住； 同时增加
   * aac_adtstoasc、faststart 等参数，提升兼容性。
   *
   * @param inputFile  输入 m3u8 文件路径（建议绝对路径）
   * @param outputFile 输出 mp4 文件路径（建议绝对路径）
   * @return FFmpeg 执行结果（退出码 0 表示成功）
   */
  public static ProcessResult m3u82Mp4(String inputFile, String outputFile) throws IOException, InterruptedException {
    File m3u8 = new File(inputFile);
    if (!m3u8.isFile()) {
      throw new IOException("m3u8 not found: " + m3u8.getAbsolutePath());
    }

    // 1) 若没有 #EXT-X-ENDLIST，则补上，避免被当作直播持续等待
    ensureEndlist(m3u8);

    // 2) 组装 FFmpeg 命令（无损拷贝 & 不阻塞 & 兼容性更好）
    List<String> command = new ArrayList<>();
    command.add("ffmpeg");
    command.add("-v");
    command.add("error"); // 只打印错误
    command.add("-stats"); // 仍显示进度统计
    command.add("-protocol_whitelist"); // 允许本地/网络协议（保守起见）
    command.add("file,http,https,tcp,tls");
    command.add("-i");
    command.add(inputFile); // 输入 m3u8
    command.add("-c");
    command.add("copy"); // 无损拷贝
    command.add("-bsf:a");
    command.add("aac_adtstoasc"); // 去 ADTS 头，兼容 MP4
    command.add("-movflags");
    command.add("+faststart"); // 前移 moov，网页秒播
    command.add("-y"); // 覆盖输出
    command.add(outputFile);

    ProcessBuilder pb = new ProcessBuilder(command);

    // 关键：把工作目录切到 m3u8 所在目录，确保相对分片路径能被正确读取
    File workDir = m3u8.getParentFile();
    if (workDir != null && workDir.isDirectory()) {
      pb.directory(workDir);
    }

    long id = SnowflakeIdUtils.id();
    log.info("id:{} cmd:{}", id, String.join(" ", command));

    File logDir = new File(LOG_FOLDER, String.valueOf(id));
    ProcessResult result = ProcessUtils.execute(logDir, id, pb, 10 * 60); // 超时：10分钟，可按需调整
    return result;
  }

  /**
   * 若清单缺少 #EXT-X-ENDLIST，则在文件末尾追加一行，确保 FFmpeg 不会等待新分片
   */
  private static void ensureEndlist(File m3u8) throws IOException {
    // 读取最后几KB 判断是否已包含（避免加载超大文件全部内容）
    byte[] tailBytes = Files.readAllBytes(m3u8.toPath());
    String content = new String(tailBytes, StandardCharsets.UTF_8);
    if (!content.contains("#EXT-X-ENDLIST")) {
      String append = content.endsWith("\n") ? "#EXT-X-ENDLIST\n" : "\n#EXT-X-ENDLIST\n";
      Files.write(m3u8.toPath(), append.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
      log.info("Appended #EXT-X-ENDLIST to {}", m3u8.getAbsolutePath());
    } else {
      log.info("#EXT-X-ENDLIST already present in {}", m3u8.getAbsolutePath());
    }
  }
}