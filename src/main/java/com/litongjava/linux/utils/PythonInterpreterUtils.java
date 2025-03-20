package com.litongjava.linux.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.jfinal.kit.Kv;
import com.litongjava.linux.vo.PythonResult;
import com.litongjava.template.PythonCodeEngine;
import com.litongjava.tio.utils.encoder.Base64Utils;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

public class PythonInterpreterUtils {
  public static PythonResult execute(String scriptPath, long id) throws IOException, InterruptedException {
    File folder = new File("images");
    if (!folder.exists()) {
      folder.mkdirs();
    }

    String fullCode = PythonCodeEngine.renderToString("main.py", Kv.by("script_path", scriptPath).set("temp_id", id));

    // 构造 ProcessBuilder
    String osName = System.getProperty("os.name");
    ProcessBuilder pb=null;
    if(osName.toLowerCase().contains("windows")) {
      pb = new ProcessBuilder("python", "-c", fullCode);
    }else {
      pb = new ProcessBuilder("python3", "-c", fullCode);
      
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
    PythonResult result = new PythonResult();
    result.setExitCode(exitCode);
    result.setStdOut(outputBuilder.toString());
    result.setStdErr(errorBuilder.toString());
    File file = new File("images" + File.separator + id + ".png");
    if (file.exists()) {
      byte[] readAllBytes = Files.readAllBytes(file.toPath());
      String base64 = Base64Utils.encodeImage(readAllBytes, "image/png");
      result.setImage(base64);
    }
    return result;
  }

  /**
   */
  public static PythonResult executeCode(String code) throws IOException, InterruptedException {

    File folder = new File("scripts");
    if (!folder.exists()) {
      folder.mkdirs();
    }
    long id = SnowflakeIdUtils.id();
    String scriptPath = "scripts" + File.separator + id + ".py";
    FileUtil.writeString(code, scriptPath, StandardCharsets.UTF_8.toString());
    return execute(scriptPath, id);
  }

  public static PythonResult executeScript(String scriptPath) throws IOException, InterruptedException {
    File folder = new File("scripts");
    if (!folder.exists()) {
      folder.mkdirs();
    }
    long id = SnowflakeIdUtils.id();
    return execute(scriptPath, id);
  }
}
