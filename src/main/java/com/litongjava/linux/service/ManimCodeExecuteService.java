package com.litongjava.linux.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.litongjava.media.NativeMedia;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.utils.commandline.CommandLineResult;
import com.litongjava.tio.utils.commandline.CommandLineUtils;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.hutool.ResourceUtil;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimCodeExecuteService {

  public CommandLineResult executeCode(String code, Boolean stream, Long sessionPrt, String m3u8Path, ChannelContext channelContext) throws IOException, InterruptedException {
    new File("cache").mkdirs();
    long id = SnowflakeIdUtils.id();
    String taskFolder = "cache" + File.separator + id;
    code = code.replace("#(output_path)", taskFolder);

    String folder = "scripts" + File.separator + id;
    File fileFolder = new File(folder);
    if (!fileFolder.exists()) {
      fileFolder.mkdirs();
    }
    String scriptPath = folder + File.separator + "script.py";
    FileUtil.writeString(code, scriptPath, StandardCharsets.UTF_8.toString());
    List<String> videoFolders = new ArrayList<>();
    addFolder(taskFolder, videoFolders);

    // 执行脚本
    CommandLineResult execute = execute(scriptPath, taskFolder);
    execute.setTaskId(id);
    String textPath = taskFolder + File.separator + "script" + File.separator + "script.txt";
    File scriptFile = new File(textPath);
    if (scriptFile.exists()) {
      String text = FileUtil.readString(scriptFile);
      execute.setText(text);
    }
    boolean found = false;
    for (String videoFolder : videoFolders) {
      String filePath = videoFolder + File.separator + "CombinedScene.mp4";
      File file = new File(filePath);
      if (file.exists()) {
        found = true;
        log.info("found file:{}", filePath);
        if (sessionPrt != null) {
          log.info("merge into:{},{}", sessionPrt, m3u8Path);
          String appendVideoSegmentToHls = NativeMedia.appendVideoSegmentToHls(sessionPrt, filePath);
          log.info("merge result:{}", appendVideoSegmentToHls);
          String videoUrl = "/" + filePath;
          execute.setOutput(videoUrl);
          execute.setVideo(videoUrl);
        } else {
          log.info("skip merge to hls:{}", filePath);

          String subPath = "./data/hls/" + id + "/";
          String name = "main";

          String relPath = subPath + name + ".mp4";
          File relPathFile = new File(relPath);
          relPathFile.getParentFile().mkdirs();

          Files.copy(file.toPath(), relPathFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
          String hlsPath = subPath + name + ".m3u8";
          log.info("to hls:{}", hlsPath);
          NativeMedia.splitVideoToHLS(hlsPath, relPath, subPath + "/" + name + "_%03d.ts", 10);
          String videoUrl = hlsPath.replace("./", "/");
          execute.setOutput(videoUrl);
          execute.setVideo(videoUrl);
        }
        break;
      }
    }
    if (!found) {
      log.error("not found video in :{}", taskFolder);
    }

    return execute;
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

  public static CommandLineResult execute(String scriptPath, String subFolder) throws IOException, InterruptedException {
    String osName = System.getProperty("os.name").toLowerCase();
    log.info("osName: {} scriptPath: {}", osName, scriptPath);
    // 获取脚本所在目录
    File scriptFile = new File(scriptPath);
    File scriptDir = scriptFile.getParentFile();
    try (InputStream in = ResourceUtil.getResourceAsStream("python/manim_utils.py")) {
      if (in == null) {
        throw new IOException("Resource not found: python/manim_utils.py");
      }
      File manimUtilsFile = new File(scriptDir, "manim_utils.py");
      Files.copy(in, manimUtilsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    //manim -ql --fps 10  --progress_bar none --verbosity WARNING --media_dir cache/01 --output_file CombinedScene scripts/01/script.py CombinedScene 
    ProcessBuilder pb = new ProcessBuilder("manim", "-ql", "--fps", "10",
        //
        "--progress_bar", "none", "--verbosity", "WARNING",
        //
        "--media_dir", subFolder, "--output_file", "CombinedScene",
        //
        scriptPath, "CombinedScene");
    pb.environment().put("PYTHONIOENCODING", "utf-8");

    CommandLineResult result = CommandLineUtils.execute(scriptDir, pb);

    return result;
  }


}
