package com.litongjava.linux.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.litongjava.linux.ProcessResult;
import com.litongjava.media.NativeMedia;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimService {

  public ProcessResult executeCode(String code, Boolean stream, Long sessionId, String m3u8Path, ChannelContext channelContext) throws IOException, InterruptedException {
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
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "720p30");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "1080p30");

    // 定义需要监控的文件夹，注意此处为绝对路径或根据实际情况调整
    // 如果需要流式发送，则启动文件夹监控线程

    // 执行脚本
    ProcessResult execute = execute(scriptPath);
    execute.setTaskId(id);
    boolean found = false;
    for (String videoFolder : videoFolders) {
      String filePath = videoFolder + File.separator + "CombinedScene.mp4";
      File file = new File(filePath);
      if (file.exists()) {
        found = true;
        log.info("found file:{}", filePath);
        execute.setOutput(filePath.replace("\\", "/"));

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
        if (sessionId != null) {
          log.info("merge into:{},{}", sessionId, m3u8Path);
          String appendVideoSegmentToHls = NativeMedia.appendVideoSegmentToHls(sessionId, filePath);
          log.info("merge result:{}", appendVideoSegmentToHls);
        } else {
          log.info("skip merge to hls");
        }
      }
    }
    if (!found) {
      log.error("not found video in :{}", subFolder);
    }

    return execute;
  }

  public static ProcessResult execute(String scriptPath) throws IOException, InterruptedException {
    String osName = System.getProperty("os.name").toLowerCase();
    log.info("osName: {} scriptPath: {}", osName, scriptPath);
    ProcessBuilder pb;
    if (osName.contains("windows") || osName.startsWith("mac")) {
      pb = new ProcessBuilder("python", scriptPath);
    } else {
      pb = new ProcessBuilder("python3", scriptPath);
    }
    pb.environment().put("PYTHONIOENCODING", "utf-8");

    // 获取脚本所在目录
    File scriptFile = new File(scriptPath);
    File scriptDir = scriptFile.getParentFile();
    if (scriptDir != null && !scriptDir.exists()) {
      scriptDir.mkdirs();
    }

    // 定义日志文件路径，存放在与 scriptPath 相同的目录
    File stdoutFile = new File(scriptDir, "stdout.log");
    File stderrFile = new File(scriptDir, "stderr.log");

    // 将输出和错误流重定向到对应的日志文件
    pb.redirectOutput(stdoutFile);
    pb.redirectError(stderrFile);

    Process process = pb.start();
    int exitCode = process.waitFor();

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
