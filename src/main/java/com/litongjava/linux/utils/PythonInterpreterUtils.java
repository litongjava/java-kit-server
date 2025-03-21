package com.litongjava.linux.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;
import com.litongjava.linux.vo.ProcessResult;
import com.litongjava.template.PythonCodeEngine;
import com.litongjava.tio.utils.encoder.Base64Utils;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

public class PythonInterpreterUtils {
  public static ProcessResult execute(String scriptPath, String script_dir) throws IOException, InterruptedException {
    String imagesDir = script_dir + File.separator + "images";
    File imagesFolder = new File(imagesDir);
    if (!imagesFolder.exists()) {
      imagesFolder.mkdirs();
    }

    String fullCode = PythonCodeEngine.renderToString("main.py", Kv.by("script_path", scriptPath).set("script_dir", script_dir));

    // 构造 ProcessBuilder
    String osName = System.getProperty("os.name");
    ProcessBuilder pb = null;
    if (osName.toLowerCase().contains("windows")) {
      pb = new ProcessBuilder("python", "-c", fullCode);
    } else {
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
    ProcessResult result = new ProcessResult();
    result.setExitCode(exitCode);
    result.setStdOut(outputBuilder.toString());
    result.setStdErr(errorBuilder.toString());
    File[] listFiles = imagesFolder.listFiles();
    if (listFiles != null && listFiles.length > 0) {
      List<String> images = new ArrayList<>();
      for (File image : listFiles) {
        byte[] readAllBytes = FileUtil.readBytes(image);
        String base64 = Base64Utils.encodeImage(readAllBytes, "image/png");
        images.add(base64);
        result.setImages(images);
      }
    }
    return result;
  }

  /**
   */
  public static ProcessResult executeCode(String code) throws IOException, InterruptedException {

    long id = SnowflakeIdUtils.id();
    String folder = "scripts" + File.separator + id;
    File fileFolder = new File(folder);
    if (!fileFolder.exists()) {
      fileFolder.mkdirs();
    }
    String scriptPath = folder + File.separator + "script.py";
    FileUtil.writeString(code, scriptPath, StandardCharsets.UTF_8.toString());
    return execute(scriptPath, folder);
  }

  public static ProcessResult executeScript(String scriptPath) throws IOException, InterruptedException {
    long id = SnowflakeIdUtils.id();
    String folder = "scripts" + File.separator + id;
    File fileFolder = new File(folder);
    if (!fileFolder.exists()) {
      fileFolder.mkdirs();
    }
    return execute(scriptPath, folder);
  }
}
