package com.litongjava.kit.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import com.litongjava.kit.vo.VideoCodeInput;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.commandline.ProcessUtils;
import com.litongjava.tio.utils.hutool.FileUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MotionCanvasCodeExecuteService {
  public ProcessResult executeCode(VideoCodeInput input, ChannelContext channelContext)
      throws IOException, InterruptedException {
    Long sessionId = input.getSessionId();
    Long taskId = input.getTaskId();
    String taskName = input.getTaskName();
    String code = input.getCode();
    int timeout = input.getTimeout();

    String templatePathStr = "motion-canvas/packages/work-template";
    String projectPathStr = "motion-canvas/packages/work-template-" + sessionId;
    String projectJsStr = projectPathStr + "/dist/src/project.js";
    File projectPath = new File(projectPathStr);
    if (!projectPath.exists()) {
      try {
        FileUtil.copyDirectory(Paths.get(templatePathStr), Paths.get(projectPathStr), true);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        return ProcessResult.buildMessage(e.getMessage());
      }
    }

    // 执行脚本
    ProcessResult result = execute(projectPath, sessionId + "_" + taskName, code, timeout);
    result.setTaskId(taskId);
    int exitCode = result.getExitCode();
    log.info("exitCode:{},{}", taskId, exitCode);
    boolean success = exitCode == 0;

    if (success) {
      File projectJs = new File(projectJsStr);
      if (projectJs.exists()) {
        String content = FileUtil.readString(projectJs);
        result.setOutput(content);
      }
    } else {
      log.error("Failed to run task:{} {}", projectPath, taskName);
    }
    return result;
  }

  public static ProcessResult execute(File projectPath, String code, String taskName, int timeout)
      throws IOException, InterruptedException {
    String osName = System.getProperty("os.name").toLowerCase();
    log.info("osName: {} scriptPath: {}", osName, projectPath);
    String cmd = "npm run build -w %s";
    cmd = String.format(cmd, projectPath);
    log.info("cmd:{}", cmd);
    ProcessBuilder pb = new ProcessBuilder("npm", "run", "build", "-w", projectPath.toString());
    ProcessResult result = ProcessUtils.execute(projectPath, taskName, pb, timeout);
    return result;
  }
}
