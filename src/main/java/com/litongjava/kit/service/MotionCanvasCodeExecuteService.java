package com.litongjava.kit.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.litongjava.kit.vo.VideoCodeInput;
import com.litongjava.media.NativeMedia;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.commandline.ProcessUtils;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.path.WorkDirUtils;

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

    String scriptSessionFolder = WorkDirUtils.workingScriptsDir() + File.separator + sessionId;
    File scriptDir = new File(scriptSessionFolder);
    if (!scriptDir.exists()) {
      scriptDir.mkdirs();
    }

    String copyCommand = "cp -r motion-canvas/packages/work-template motion-canvas/packages/work-template-" + sessionId;

    // 执行脚本
    ProcessResult result = execute(taskId, taskName, code, timeout);
    result.setTaskId(taskId);
    // 读取文字
    String textPath = WorkDirUtils.workingMediaDir + File.separator + "tts_text" + File.separator + taskId + ".txt";
    File scriptFile = new File(textPath);
    if (scriptFile.exists()) {
      String text = FileUtil.readString(scriptFile);
      result.setText(text);
    }

    // 读取字幕
    String subtitlePath = WorkDirUtils.workingMediaDir + File.separator + "tts_subtitle" + File.separator + taskId
        + ".vtt";
    File subtitleFile = new File(subtitlePath);
    if (subtitleFile.exists()) {
      String text = FileUtil.readString(subtitleFile);
      result.setSubtitle(text);
    }

    int exitCode = result.getExitCode();
    log.info("exitCode:{},{}", taskId, exitCode);
    boolean success = exitCode == 0;

    if (success) {
      String dataHlsVideoDir = "./data" + "/" + "hls" + "/" + taskId;
      File file = new File(dataHlsVideoDir);
      if (!file.exists()) {
        file.mkdirs();
      }
      boolean found = false;
      for (String videoFolder : videoFolders) {
        File videoDir = new File(videoFolder);
        if (!videoDir.exists()) {
          continue;
        }
        found = true;
        File[] mp4Files = videoDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));

        String videoFilePath = null;
        if (mp4Files.length > 1) {
          Arrays.sort(mp4Files, Comparator.comparing(File::getName));
          String[] mp4FilePaths = new String[mp4Files.length];
          List<String> imagesFilePaths = new ArrayList<>(mp4Files.length);

          for (int i = 0; i < mp4Files.length; i++) {
            File mp4File = mp4Files[i];
            String mp4Path = mp4File.getAbsolutePath();
            String outputJpgPath = dataHlsVideoDir + "/" + mp4File.getName() + ".jpg";
            int saveExitCode = NativeMedia.saveLastFrame(mp4Path, outputJpgPath);
            if (saveExitCode == 0) {
              imagesFilePaths.add(outputJpgPath.replace("./", "/"));
            }
            mp4FilePaths[i] = mp4Path;
          }
          result.setImages(imagesFilePaths);
          videoFilePath = videoFolder + File.separator + "CombinedScene.mp4";
          NativeMedia.merge(mp4FilePaths, videoFilePath);
        } else {
          // 必须返回相对路径
          videoFilePath = videoFolder + File.separator + mp4Files[0].getName();
        }

        file = new File(videoFilePath);
        if (file.exists()) {
          double videoLength = NativeMedia.getVideoLength(videoFilePath);
          result.setVideo_length(videoLength);
          log.info("found file:{},{}", videoFilePath, videoLength);
          if (sessionPrt != null) {
            String outputJpgPath = dataHlsVideoDir + "/" + file.getName() + ".jpg";
            int saveExitCode = NativeMedia.saveLastFrame(videoFilePath, outputJpgPath);
            if (saveExitCode == 0) {
              result.setImage(outputJpgPath.replace("./", "/"));
            }

            log.info("merge into:{},{}", sessionPrt, m3u8Path);
            String appendVideoSegmentToHls = NativeMedia.appendVideoSegmentToHls(sessionPrt, videoFilePath);
            log.info("merge result:{}", appendVideoSegmentToHls);
            String videoUrl = "/" + videoFilePath;
            result.setOutput(videoUrl);
            result.setVideo(videoUrl);
          } else {
            log.info("skip merge to hls:{}", videoFilePath);

            String name = "main";

            String relPath = dataHlsVideoDir + File.separator + name + ".mp4";
            File relPathFile = new File(relPath);
            relPathFile.getParentFile().mkdirs();

            Files.copy(file.toPath(), relPathFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            String dataM3u8Path = dataHlsVideoDir + "/" + name + ".m3u8";
            log.info("to hls:{}", dataM3u8Path);
            NativeMedia.splitVideoToHLS(dataM3u8Path, relPath, dataHlsVideoDir + "/" + name + "_%03d.ts", 10);
            String videoUrl = dataM3u8Path.replace("./", "/");
            result.setOutput(videoUrl);
            result.setVideo(videoUrl);
          }
          break;
        }
      }
      if (!found) {
        log.error("not found video:{},id:{}", WorkDirUtils.workingMediaDir, taskId);
      }
    } else {
      log.error("Failed to run task:{}", scriptPath);
    }
    return result;
  }

  public static ProcessResult execute(String code, String projectPath, String taskName, int timeout)
      throws IOException, InterruptedException {
    String osName = System.getProperty("os.name").toLowerCase();
    log.info("osName: {} scriptPath: {}", osName, projectPath);
    // manim -ql --fps 10 --progress_bar none --verbosity WARNING --media_dir
    // cache/01 --output_file CombinedScene scripts/01/script.py CombinedScene
    String cmd = "manim -q%s --fps 10  --progress_bar none --verbosity WARNING %s -a";
    cmd = String.format(cmd, taskName, projectPath);
    log.info("cmd:{}", cmd);
    

    ProcessBuilder pb = new ProcessBuilder("npm", "run", "build", "-w", projectPath);

    String workingDir = WorkDirUtils.getWorkingDir();

    ProcessResult result = ProcessUtils.execute(projectPath, taskName, pb, timeout);

    return result;
  }

}
