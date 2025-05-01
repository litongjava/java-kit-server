package com.litongjava.linux.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.litongjava.linux.ProcessResult;
import com.litongjava.media.NativeMedia;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.hutool.ResourceUtil;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimCodeExecuteService {

  public ProcessResult executeCode(String code, Boolean stream, Long sessionPrt, String m3u8Path, ChannelContext channelContext) throws IOException, InterruptedException {
    new File("cache").mkdirs();
    long id = SnowflakeIdUtils.id();
    String subFolder = "cache" + File.separator + id;
    code = code.replace("#(output_path)", subFolder);

    String folder = "scripts" + File.separator + id;
    File fileFolder = new File(folder);
    if (!fileFolder.exists()) {
      fileFolder.mkdirs();
    }
    String scriptPath = folder + File.separator + "script.py";
    FileUtil.writeString(code, scriptPath, StandardCharsets.UTF_8.toString());
    List<String> videoFolders = new ArrayList<>();
    addFolder(subFolder, videoFolders);

    // 执行脚本
    ProcessResult execute = execute(scriptPath, subFolder);
    execute.setTaskId(id);
    boolean found = false;
    for (String videoFolder : videoFolders) {
      String filePath = videoFolder + File.separator + "CombinedScene.mp4";
      File file = new File(filePath);
      if (file.exists()) {
        found = true;
        log.info("found file:{}", filePath);
        if (sessionPrt != null) {
          log.info("merge into:{},{}", sessionPrt, m3u8Path);
          String appendVideoSegmentToHls = NativeMedia.appendVideoSegmentToHls(sessionPrt, filePath);
          log.info("merge result:{}", appendVideoSegmentToHls);
          execute.setOutput("/" + filePath);
        } else {
          log.info("skip merge to hls:{}", filePath);

          String subPath = "./data/hls/" + id + "/";
          String name = "main";

          String relPath = subPath + name + ".mp4";
          File relPathFile = new File(relPath);
          relPathFile.getParentFile().mkdirs();

          Files.copy(file.toPath(), relPathFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
          String hlsPath = subPath + name + ".m3u8";
          log.info("to hls:{}", hlsPath);
          NativeMedia.splitVideoToHLS(hlsPath, relPath, subPath + "/" + name + "_%03d.ts", 10);
          execute.setOutput(hlsPath.replace("./", "/"));
        }
        break;
      }
    }
    if (!found) {
      log.error("not found video in :{}", subFolder);
    }

    return execute;
  }

  private void addFolder(String subFolder, List<String> videoFolders) {
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "480p30");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "480p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "720p30");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "720p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "1080p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "1080p30");

    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "480p30");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "480p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "720p30");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "720p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "1080p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "1080p30");
  }

  public static ProcessResult execute(String scriptPath, String subFolder) throws IOException, InterruptedException {
    String osName = System.getProperty("os.name").toLowerCase();
    log.info("osName: {} scriptPath: {}", osName, scriptPath);
    // 获取脚本所在目录
    File scriptFile = new File(scriptPath);
    File scriptDir = scriptFile.getParentFile();
    if (scriptDir != null && !scriptDir.exists()) {
      scriptDir.mkdirs();
    }
    try (InputStream in = ResourceUtil.getResourceAsStream("python/manim_utils.py")) {
      if (in == null) {
        throw new IOException("Resource not found: python/manim_utils.py");
      }
      File manimUtilsFile = new File(scriptDir, "manim_utils.py");
      Files.copy(in, manimUtilsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    //manim -ql --fps 15 --media_dir cache/01 --output_file CombinedScene scripts/01/script.py CombinedScene 
    ProcessBuilder pb = new ProcessBuilder("manim", "-ql", "--fps", "15",
        //
        "--media_dir", subFolder, "--output_file", "CombinedScene",
        //
        scriptPath, "CombinedScene");
    pb.environment().put("PYTHONIOENCODING", "utf-8");

    // 定义日志文件路径，存放在与 scriptPath 相同的目录
    File stdoutFile = new File(scriptDir, "stdout.log");
    File stderrFile = new File(scriptDir, "stderr.log");

    // 将输出和错误流重定向到对应的日志文件
    pb.redirectOutput(stdoutFile);
    pb.redirectError(stderrFile);

    Process process = pb.start();

    // 等待40秒，如果超过40秒仍未响应，则强制终止进程
    boolean finished = process.waitFor(120, TimeUnit.SECONDS);
    int exitCode;
    if (!finished) {
      log.error("Python process did not respond within 120 seconds. Forcibly terminating...");
      process.destroyForcibly();
      exitCode = -1; // 特殊退出码表示超时
    } else {
      exitCode = process.exitValue();
    }

    // 读取日志文件内容，返回给客户端（如果需要实时返回，可用其他方案监控文件变化）
    String stdoutContent = new String(Files.readAllBytes(stdoutFile.toPath()), StandardCharsets.UTF_8);
    String stderrContent = new String(Files.readAllBytes(stderrFile.toPath()), StandardCharsets.UTF_8);

    ProcessResult result = new ProcessResult();
    result.setExitCode(exitCode);
    result.setStdOut(stdoutContent);
    result.setStdErr(stderrContent);

    return result;
  }
}
