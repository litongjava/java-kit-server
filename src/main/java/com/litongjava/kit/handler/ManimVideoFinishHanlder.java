package com.litongjava.kit.handler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.litongjava.jfinal.aop.Aop;
import com.litongjava.kit.service.HlsSessionService;
import com.litongjava.kit.utils.FolderUtils;
import com.litongjava.kit.utils.PptUtils;
import com.litongjava.media.NativeMedia;
import com.litongjava.media.utils.VideoWaterUtils;
import com.litongjava.tio.boot.admin.services.storage.StorageUploadService;
import com.litongjava.tio.boot.admin.vo.UploadInput;
import com.litongjava.tio.boot.admin.vo.UploadResultVo;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.hutool.FilenameUtils;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimVideoFinishHanlder implements HttpRequestHandler {

  /**
   * session_prt,m3u8_path,videos,watermark
   */
  @Override
  public HttpResponse handle(HttpRequest request) throws Exception {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    Long sessionId = request.getLong("session_id");
    String watermark = request.getString("watermark");
    String storagePlatform = request.getString("storage_platform");

    ProcessResult processResult = new ProcessResult();
    log.info("sessionId:{},m3u8Path:{},watermark:{}", sessionId, watermark);
    Aop.get(HlsSessionService.class).close(sessionId);

    if (sessionId != null) {
      finish(sessionId, watermark, storagePlatform, processResult);
    }
    return response.setJson(processResult);
  }

  private void finish(Long sessionId, String watermark, String storagePlatform, ProcessResult processResult) {
    String scenesDir = FolderUtils.scenes(sessionId);
    String combinedDir = FolderUtils.combined(sessionId);

    File combinedFile = new File(combinedDir);

    if (!combinedFile.exists()) {
      combinedFile.mkdirs();
    }

    String outputMp4Path = combinedDir + File.separator + "combined.mp4";
    String outputWatermarkPath = combinedDir + File.separator + "combined_watermark.mp4";
    String outputPptPath = combinedDir + File.separator + "combined.pptx";
    String outputMp3Path = null;

    File videoDir = new File(scenesDir);
    // 1. 图片所在目录
    File[] imageFiles = videoDir.listFiles(f -> f.isFile() && FilenameUtils.isImageFile(f.getName()));
    // 生成pptx
    PptUtils.addImage(imageFiles, outputPptPath);

    File[] mp4Files = videoDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
    if (mp4Files.length > 0) {
      Arrays.sort(mp4Files, Comparator.comparing(File::getName));
      String[] mp4FileList = new String[mp4Files.length];
      for (int i = 0; i < mp4Files.length; i++) {
        String absolutePath = mp4Files[i].getAbsolutePath();
        mp4FileList[i] = absolutePath;
      }

      log.info("merge:{}", JsonUtils.toJson(mp4FileList));
      boolean merged = NativeMedia.merge(mp4FileList, outputMp4Path);
      log.info("merged:{}", merged);

      if (merged) {
        if (watermark != null) {
          try {
            VideoWaterUtils.addWatermark(outputMp4Path, outputWatermarkPath, 24, watermark);
          } catch (IOException | InterruptedException e) {
            log.error(e.getMessage(), e);
          }
        }
        double videoLength = NativeMedia.getVideoLength(outputWatermarkPath);
        log.info("video_length:{} {}", outputMp4Path, videoLength);
        processResult.setVideo_length(videoLength);
        // 实验的结果
        double delta = videoLength - 120d;
        if (delta > 0) {
          double insertion_silence_duration = delta / 100;
          outputMp3Path = NativeMedia.toMp3ForSilence(outputWatermarkPath, insertion_silence_duration);
        } else {
          outputMp3Path = NativeMedia.toMp3(outputWatermarkPath);
        }
        processResult.setAudio(outputMp3Path);
        log.info("mp3:{}", outputMp3Path);
      }
    }

    // upload
    String combinedPath = "data/combined/" + sessionId;
    List<UploadInput> uploadFiles = new ArrayList<>();
    if (Files.exists(Paths.get(outputMp4Path))) {
      String targetName = combinedPath + "/" + "combined.mp4";
      processResult.setVideo(targetName);
      uploadFiles.add(new UploadInput(outputMp4Path, targetName));
    }

    if (Files.exists(Paths.get(outputWatermarkPath))) {
      String targetName = combinedPath + "/" + "combined_watermark.mp4";
      processResult.setOutput(targetName);
      uploadFiles.add(new UploadInput(outputWatermarkPath, targetName));
    }

    if (Files.exists(Paths.get(outputMp3Path))) {
      String targetName = combinedPath + "/" + "combined_watermark.mp3";
      processResult.setAudio(targetName);
      uploadFiles.add(new UploadInput(outputMp3Path, targetName));
    }

    if (Files.exists(Paths.get(outputPptPath))) {
      String targetName = combinedPath + "/" + "combined.pptx";
      processResult.setPpt(targetName);
      uploadFiles.add(new UploadInput(outputPptPath, targetName));
    }

    if (storagePlatform != null) {
      try {
        List<UploadResultVo> results = Aop.get(StorageUploadService.class).uploadFile(storagePlatform, uploadFiles);
        processResult.setVideo(results.get(0).getUrl());
        processResult.setOutput(results.get(1).getUrl());
        processResult.setAudio(results.get(2).getUrl());
        processResult.setPpt(results.get(3).getUrl());
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }
    }

  }
}
