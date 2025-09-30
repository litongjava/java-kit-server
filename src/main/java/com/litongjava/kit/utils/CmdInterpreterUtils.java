package com.litongjava.kit.utils;

import java.io.File;
import java.io.IOException;

import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.commandline.ProcessUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

public class CmdInterpreterUtils {

  public static ProcessResult executeCmd(String cmd) {
    ProcessResult result = new ProcessResult();
    ProcessBuilder processBuilder;
    String osName = System.getProperty("os.name").toLowerCase();
    // 根据不同的操作系统使用合适的命令解释器
    if (osName.contains("windows")) {
      processBuilder = new ProcessBuilder("cmd.exe", "/c", cmd);
    } else {
      processBuilder = new ProcessBuilder("bash", "-c", cmd);
    }
    long id = SnowflakeIdUtils.id();
    String folder = "scripts" + File.separator + id;
    File fileFolder = new File(folder);
    if (!fileFolder.exists()) {
      fileFolder.mkdirs();
    }

    try {
      result = ProcessUtils.execute(fileFolder, processBuilder);
      result.setTaskId(id);
    } catch (IOException | InterruptedException e) {
      result.setStdErr(e.getMessage());
      result.setExitCode(-1);
    }
    return result;
  }
}
