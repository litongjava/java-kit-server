package com.litongjava.linux.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.jfinal.kit.Kv;
import com.jfinal.template.Engine;
import com.jfinal.template.Template;
import com.litongjava.linux.ProcessResult;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

public class ManimService {

  public ProcessResult executeCode(String code) throws IOException, InterruptedException {
    long id = SnowflakeIdUtils.id();
    Template template = Engine.use().getTemplateByString(code);
    code = template.renderToString(Kv.by("output_path", id + ""));

    String folder = "scripts" + File.separator + id;
    File fileFolder = new File(folder);
    if (!fileFolder.exists()) {
      fileFolder.mkdirs();
    }
    String scriptPath = folder + File.separator + "script.py";
    FileUtil.writeString(code, scriptPath, StandardCharsets.UTF_8.toString());
    ProcessResult execute = execute(scriptPath);
    execute.setTaskId(id);
    File file = new File(id + File.separator + "videos" + File.separator + "1080p60" + File.separator + "CombinedScene.mp4");
    if (file.exists()) {
      execute.setOutput(file.getAbsolutePath());
    }
    return execute;
  }

  public static ProcessResult execute(String scriptPath) throws IOException, InterruptedException {
    // 构造 ProcessBuilder
    String osName = System.getProperty("os.name");
    ProcessBuilder pb = null;
    if (osName.toLowerCase().contains("windows")) {
      pb = new ProcessBuilder("python", scriptPath);
    } else {
      pb = new ProcessBuilder("python3", scriptPath);

    }
    Process process = pb.start();

    // 读取标准输出 (可能包含base64以及脚本本身的print信息)
    BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

    // 读取错误输出
    BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

    // 用于存放所有的标准输出行
    StringBuilder outputBuilder = new StringBuilder();

    String line;
    while ((line = stdInput.readLine()) != null) {
      outputBuilder.append(line).append("\n");
    }

    // 收集错误输出
    StringBuilder errorBuilder = new StringBuilder();
    while ((line = stdError.readLine()) != null) {
      errorBuilder.append(line).append("\n");
    }

    // 等待进程结束
    int exitCode = process.waitFor();

    // 构造返回实体
    ProcessResult result = new ProcessResult();
    result.setExitCode(exitCode);
    result.setStdOut(outputBuilder.toString());
    result.setStdErr(errorBuilder.toString());
    return result;
  }

}
