package com.litongjava.kit.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.commandline.ProcessUtils;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.path.WorkDirUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimImageCodeExecuteService {

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
    ProcessResult execute = execute(scriptPath, id);
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

  public static ProcessResult execute(String scriptPath, long taskId) throws IOException, InterruptedException {
    String osName = System.getProperty("os.name").toLowerCase();
    log.info("osName: {} scriptPath: {}", osName, scriptPath);

    // 获取脚本所在目录
    File scriptFile = new File(scriptPath);
    File scriptDir = scriptFile.getParentFile();
    if (scriptDir != null && !scriptDir.exists()) {
      scriptDir.mkdirs();
    }

    String cmd = "manim -s -qh --format=png " + scriptPath;
    log.info("cmd:{}", cmd);
    ProcessBuilder pb = new ProcessBuilder("manim", "-s", "-qh", "--format=png", scriptPath);
    String workingDir = WorkDirUtils.getWorkingDir();
    pb.environment().put("PYTHONIOENCODING", "utf-8");
    if (EnvUtils.isDev()) {
      String str = EnvUtils.getStr("PYTHONPATH");
      if (str != null) {
        pb.environment().put("PYTHONPATH", str);
      } else {
        pb.environment().put("PYTHONPATH", workingDir);
      }
    } else {
      pb.environment().put("PYTHONPATH", workingDir);
    }
    pb.environment().put("TASK_ID", String.valueOf(taskId));

    ProcessResult result = ProcessUtils.execute(scriptDir, taskId + "", pb, 120);

    return result;
  }
}
