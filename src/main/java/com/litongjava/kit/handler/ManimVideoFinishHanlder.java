package com.litongjava.kit.handler;

import java.io.File;

import com.litongjava.media.NativeMedia;
import com.litongjava.media.utils.VideoWaterUtils;
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

    Long session_prt = request.getLong("session_prt");
    ProcessResult processResult = new ProcessResult();
    if (session_prt == null) {
      processResult.setStdErr("m3u8_path can not be empty");
      return response.setJson(processResult);
    }
    String m3u8Path = request.getString("m3u8_path");
    if (m3u8Path == null) {
      processResult.setStdErr("m3u8_path can not be empty");
      return response.setJson(processResult);
    } else {
      m3u8Path = "." + m3u8Path;
    }
    String videos = request.getString("videos");
    String watermark = request.getString("watermark");

    log.info("session_prt:{},m3u8Path:{},videos:{},watermark:{}", session_prt, m3u8Path, videos, watermark);

    File file = new File(m3u8Path);
    if (file.exists()) {
      log.info("finishPersistentHls:{}", session_prt);
      if (session_prt != null) {
        NativeMedia.finishPersistentHls(session_prt, m3u8Path);
      }
    } else {
      log.info("freeHlsSession:{}", session_prt);
      if (session_prt != null) {
        NativeMedia.freeHlsSession(session_prt);
      }
    }

    if (videos != null) {
      String subPath = FilenameUtils.getSubPath(m3u8Path);
      String outputMp4Path = subPath + "/origin_main.mp4";
      String[] split = videos.split(",");
      String[] mp4FileList = new String[split.length];
      String[] wavFileList = new String[split.length];
      for (int i = 0; i < split.length; i++) {
        mp4FileList[i] = "." + split[i].replace(".m3u8", ".mp4");
        wavFileList[i] = "." + split[i].replace(".m3u8", ".wav");
      }
      log.info("merge:{}", JsonUtils.toJson(mp4FileList));
      boolean merged = NativeMedia.merge(mp4FileList, outputMp4Path);
      log.info("merged:{}", merged);
      if (merged) {
        String outputFile = outputMp4Path;
        if (watermark != null) {
          outputFile = subPath + "/main.mp4";
          VideoWaterUtils.addWatermark(outputMp4Path, outputFile, 24, watermark);
        }

        double videoLength = NativeMedia.getVideoLength(outputFile);
        log.info("video_length:{} {}", outputMp4Path, videoLength);
        processResult.setVideo_length(videoLength);

        // 实验的结果
        double delta = videoLength - 120d;
        String mp3Path = null;
        if (delta > 0) {
          double insertion_silence_duration = delta / 100;
          mp3Path = NativeMedia.toMp3ForSilence(outputFile, insertion_silence_duration);
        } else {
          mp3Path = NativeMedia.toMp3(outputFile);
        }
        processResult.setAudio(mp3Path);
        log.info("mp3:{}", mp3Path);
      }
    }

    return response.setJson(processResult);
  }

}
