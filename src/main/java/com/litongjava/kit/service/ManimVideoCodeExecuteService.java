package com.litongjava.kit.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.litongjava.kit.vo.ManimVideoCodeInput;
import com.litongjava.media.NativeMedia;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.commandline.ProcessUtils;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.hutool.ResourceUtil;
import com.litongjava.tio.utils.path.WorkDirUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimVideoCodeExecuteService {

  public ProcessResult executeCode(ManimVideoCodeInput input, ChannelContext channelContext)
      throws IOException, InterruptedException {
    String cacheDir = WorkDirUtils.workingCacheDir();
    Long id = input.getId();
    String code = input.getCode();
    String quality = input.getQuality();
    int timeout = input.getTimeout();
    Long sessionPrt = input.getSessionPrt();
    String m3u8Path = input.getM3u8Path();
    String taskFolder = cacheDir + File.separator + id;
    code = code.replace("#(output_path)", taskFolder);

    String folder = WorkDirUtils.workingScriptsDir() + File.separator + id;
    File scriptDir = new File(folder);
    if (!scriptDir.exists()) {
      scriptDir.mkdirs();
    }
    String scriptPath = folder + File.separator + "script.py";
    FileUtil.writeString(code, scriptPath, StandardCharsets.UTF_8.toString());
    List<String> videoFolders = new ArrayList<>();
    addFolder(taskFolder, videoFolders);

    // 执行脚本
    ProcessResult result = execute(scriptPath, taskFolder, timeout, quality);
    result.setTaskId(id);
    // 读取字幕
    String textPath = taskFolder + File.separator + "script" + File.separator + "script.txt";
    File scriptFile = new File(textPath);
    if (scriptFile.exists()) {
      String text = FileUtil.readString(scriptFile);
      result.setText(text);
    }
    int exitCode = result.getExitCode();
    log.info("exitCode:{},{}", id, exitCode);
    boolean success = exitCode == 0;

    if (success) {
      String dataHlsVideoDir = "./data" + "/" + "hls" + "/" + id;
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
        log.error("not found video:{}", taskFolder);
      }
    } else {
      log.error("Failed to run task:{}", taskFolder);
    }
    return result;
  }

  private void addFolder(String subFolder, List<String> videoFolders) {
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "480p30");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "480p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "480p10");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "720p30");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "720p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "720p10");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "1080p30");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "1080p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "1080p10");

    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "480p30");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "480p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "480p10");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "720p30");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "720p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "720p10");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "1080p30");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "1080p15");
    videoFolders.add(subFolder + File.separator + "videos" + File.separator + "script" + File.separator + "1080p10");
  }

  public static ProcessResult execute(String scriptPath, String mediaDir, int timeout, String quality)
      throws IOException, InterruptedException {
    String osName = System.getProperty("os.name").toLowerCase();
    log.info("osName: {} scriptPath: {}", osName, scriptPath);
    // 获取脚本所在目录
    File scriptFile = new File(scriptPath);
    File scriptDir = scriptFile.getParentFile();
    try (InputStream in = ResourceUtil.getResourceAsStream("python/manim_toolkit.py")) {
      if (in == null) {
        throw new IOException("Resource not found: python/manim_toolkit.py");
      }
      File manimUtilsFile = new File(scriptDir, "manim_toolkit.py");
      Files.copy(in, manimUtilsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // manim -ql --fps 10 --progress_bar none --verbosity WARNING --media_dir
    // cache/01 --output_file CombinedScene scripts/01/script.py CombinedScene
    String cmd = "manim -qh --fps 10  --progress_bar none --verbosity WARNING --media_dir %s %s -a";
    cmd = String.format(cmd, mediaDir, "script.py");
    File runSh = new File(scriptDir, "run.sh");
    FileUtil.writeString(cmd, runSh);

    ProcessBuilder pb = new ProcessBuilder("manim", "-q" + quality, "--fps", "10",
        //
        "--progress_bar", "none", "--verbosity", "WARNING",
        //
        "--media_dir", mediaDir,
        //
        scriptPath, "-a");

    String workingDir = WorkDirUtils.getWorkingDir();
    pb.environment().put("PYTHONIOENCODING", "utf-8");
    pb.environment().put("PYTHONPATH", workingDir);

    ProcessResult result = ProcessUtils.execute(scriptDir, pb, timeout);

    return result;
  }

}
