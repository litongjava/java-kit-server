package com.litongjava.kit.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import com.jfinal.kit.Kv;
import com.litongjava.kit.vo.VideoCodeInput;
import com.litongjava.linux.SessionFinishRequest;
import com.litongjava.template.TemplateEngine;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.commandline.ProcessUtils;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MotionCanvasCodeExecuteService {
  public ProcessResult executeCode(VideoCodeInput input, ChannelContext channelContext) throws IOException, InterruptedException {
    Long sessionId = input.getSessionId();
    Long taskId = input.getTaskId();
    String taskName = input.getTaskName();
    String code = input.getCode();
    int timeout = input.getTimeout();

    String templatePathStr = "motion-canvas/packages/work-template";
    String targetSubProjectPathStr = "packages/work-template-" + sessionId;
    String projectPathStr = "motion-canvas/" + targetSubProjectPathStr;
    String projectJsonStr = projectPathStr + "/package.json";
    String projectTsStr = projectPathStr + "/src/project.ts";
    String sceneTsStr = projectPathStr + "/src/scenes/" + taskName + ".tsx";
    String projectJsStr = projectPathStr + "/dist/src/project.js";

    File projectPath = new File(projectPathStr);

    if (!projectPath.exists()) {
      try {
        FileUtil.copyDirectory(Paths.get(templatePathStr), Paths.get(projectPathStr), true);
        String projectJsonContent = TemplateEngine.renderToString("package.json", Kv.by("sessionId", sessionId));
        FileUtil.writeString(projectJsonContent, projectJsonStr);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        return ProcessResult.buildMessage(e.getMessage());
      }
    }

    String projectTsContent = TemplateEngine.renderToString("project.ts", Kv.by("scene_name", taskName));
    FileUtil.writeString(projectTsContent, projectTsStr);

    FileUtil.writeString(code, sceneTsStr);
    // 执行脚本
    ProcessResult result = execute(projectPath, targetSubProjectPathStr, sessionId + "_" + taskName, timeout);
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

  public static ProcessResult execute(File projectPath, String subProject, String taskName, int timeout)
      throws IOException, InterruptedException {
    String osName = System.getProperty("os.name").toLowerCase();
    log.info("osName: {} scriptPath: {}", osName, subProject);
    String cmd = "npm run build -w %s";
    cmd = String.format(cmd, subProject);
    log.info("cmd:{}", cmd);
    ProcessBuilder pb = new ProcessBuilder("npm", "run", "build", "-w", subProject);
    pb.directory(new File("motion-canvas"));
    ProcessResult result = ProcessUtils.execute(projectPath, taskName, pb, timeout);
    return result;
  }

  public ProcessResult finish(SessionFinishRequest modelRequest) throws IOException, InterruptedException {
    String[] sceneNames = modelRequest.getSceneNames();
    Long sessionId = modelRequest.getSessionId();

    String targetSubProjectPathStr = "packages/work-template-" + sessionId;
    String projectPathStr = "motion-canvas/" + targetSubProjectPathStr;

    String projectTsStr = projectPathStr + "/src/project.ts";
    String projectJsStr = projectPathStr + "/dist/src/project.js";

    File projectPath = new File(projectPathStr);
    if (!projectPath.exists()) {
      projectPath.mkdirs();
    }

    String projectTsContent = TemplateEngine.renderToString("project-full.ts", Kv.by("sceneNames", sceneNames));
    FileUtil.writeString(projectTsContent, projectTsStr);

    // 执行脚本
    String taskName = "finish_" + sessionId;
    ProcessResult result = execute(projectPath, targetSubProjectPathStr, taskName, 10 * 1000);
    long taskId = SnowflakeIdUtils.id();
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
}
