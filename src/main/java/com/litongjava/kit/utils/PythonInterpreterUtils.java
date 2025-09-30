package com.litongjava.kit.utils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;
import com.litongjava.template.PythonCodeEngine;
import com.litongjava.tio.utils.base64.Base64Utils;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.commandline.ProcessUtils;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PythonInterpreterUtils {
  public static ProcessResult execute(String scriptPath, String script_dir) {
    String imagesDir = script_dir + File.separator + "images";
    File imagesFolder = new File(imagesDir);
    if (!imagesFolder.exists()) {
      imagesFolder.mkdirs();
    }

    String fullCode = PythonCodeEngine.renderToString("main.py",
        Kv.by("script_path", scriptPath).set("script_dir", script_dir));

    // 构造 ProcessBuilder
    String osName = System.getProperty("os.name").toLowerCase();
    ProcessBuilder processBuilder = null;

    try {
      // 先检测 python 是否存在
      Process checkPython = null;
      if (osName.contains("windows")) {
        checkPython = new ProcessBuilder("where", "python").start();
      } else {
        checkPython = new ProcessBuilder("which", "python").start();
      }

      int exitCode = checkPython.waitFor();
      if (exitCode == 0) {
        // python 存在
        processBuilder = new ProcessBuilder("python", "-c", fullCode);
      } else {
        // python 不存在，尝试 python3
        processBuilder = new ProcessBuilder("python3", "-c", fullCode);
      }

    } catch (Exception e) {
      // 出现异常时默认使用 python3
      processBuilder = new ProcessBuilder("python3", "-c", fullCode);
    }

    File file = new File(script_dir);
    ProcessResult result = null;
    try {
      result = ProcessUtils.execute(file, processBuilder);
    } catch (IOException | InterruptedException e) {
      result = new ProcessResult();
      result.setStdErr(e.getMessage());
      result.setExitCode(-1);
      return result;
    }

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
  public static ProcessResult executeCode(String code) {

    long id = SnowflakeIdUtils.id();
    String folder = "scripts" + File.separator + id;
    File fileFolder = new File(folder);
    if (!fileFolder.exists()) {
      fileFolder.mkdirs();
    }
    String scriptPath = folder + File.separator + "script.py";
    try {
      FileUtil.writeString(code, scriptPath, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      log.error(e.getMessage(), e);
      return ProcessResult.buildMessage(e.getMessage());
    }
    ProcessResult execute = execute(scriptPath, folder);
    execute.setTaskId(id);
    return execute;
  }

  public static ProcessResult executeScript(String scriptPath) {
    long id = SnowflakeIdUtils.id();
    String folder = "scripts" + File.separator + id;
    File fileFolder = new File(folder);
    if (!fileFolder.exists()) {
      fileFolder.mkdirs();
    }
    return execute(scriptPath, folder);
  }
}
