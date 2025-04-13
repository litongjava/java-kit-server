package com.litongjava.linux.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.litongjava.linux.ProcessResult;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimImageService {

  public ProcessResult executeCode(String code) throws IOException, InterruptedException {
    long id = SnowflakeIdUtils.id();

    String folder = "scripts" + File.separator + id;
    File fileFolder = new File(folder);
    if (!fileFolder.exists()) {
      fileFolder.mkdirs();
    }
    String scriptPath = folder + File.separator + id + ".py";
    FileUtil.writeString(code, scriptPath, StandardCharsets.UTF_8.toString());
    String imageFolder = "media" + File.separator + "images" + File.separator + id;
    log.info("imageFolder:{}", imageFolder);
    // 执行脚本
    ProcessResult execute = execute(scriptPath);
    execute.setTaskId(id);
    File imageFolderFile = new File(imageFolder);
    if (imageFolderFile.exists()) {
      File[] listFiles = imageFolderFile.listFiles();
      log.info("listFiles length:{}", listFiles.length);
      for (File file : listFiles) {
        if (file.getName().endsWith(".png")) {
          String name = file.getName();
          String filePath = imageFolder + File.separator + name;
          String newFilePath = filePath.replace("\\", "/");
          execute.setOutput("/" + newFilePath);
        }
      }
    } else {
      log.error("not exits:{}", imageFolder);
    }
    return execute;
  }

  public static ProcessResult execute(String scriptPath) throws IOException, InterruptedException {
    String osName = System.getProperty("os.name").toLowerCase();
    log.info("osName: {} scriptPath: {}", osName, scriptPath);
    ProcessBuilder pb = new ProcessBuilder("manim", "-s", "-qh", "--format=png", scriptPath);
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
