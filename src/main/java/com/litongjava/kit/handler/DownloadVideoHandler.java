package com.litongjava.kit.handler;

import java.io.File;
import java.io.IOException;

import com.litongjava.kit.utils.FfmpegUtils;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.boot.utils.HttpFileDataUtils;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.http.ContentTypeUtils;
import com.litongjava.tio.utils.hutool.FilenameUtils;
import com.litongjava.tio.utils.hutool.StrUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DownloadVideoHandler {

  public HttpResponse index(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    String path = request.getString("path");
    String filename = request.getString("filename");
    if (StrUtil.isNotBlank(filename)) {
      filename += ".mp4";
    } else {
      filename = "main.mp4";
    }

    if (StrUtil.isBlank(path)) {
      return response.body("path can not be empty");
    }

    log.info("path:{}", path);

    // 1) 获取目标文件
    String targetFile = "." + path;
    File file = new File(targetFile);
    if (!file.exists()) {
      response.setStatus(404);
      return response;
    }

    // 2) 如果是 m3u8：若 mp4 不存在或存在但不可播放，则转换/重转
    String suffix = FilenameUtils.getSuffix(targetFile);
    if ("m3u8".equalsIgnoreCase(suffix)) {
      String subPath = FilenameUtils.getSubPath(targetFile);
      String baseName = FilenameUtils.getBaseName(targetFile);
      String mp4FilePath = subPath + File.separator + baseName + ".mp4";
      File mp4File = new File(mp4FilePath);

      boolean needConvert = true;

      // 已存在：校验是否可播放，不可播放则删除并重转
      if (mp4File.exists()) {
        try {
          if (FfmpegUtils.isMp4Playable(mp4File)) {
            needConvert = false; // 直接复用
            log.info("检测到已存在且可播放的 MP4：{}", mp4FilePath);
          } else {
            log.warn("检测到 MP4 不可播放，删除后重转：{}", mp4FilePath);
            if (!mp4File.delete()) {
              log.warn("删除失败：{}，将尝试覆盖写入", mp4FilePath);
            }
          }
        } catch (Exception e) {
          log.warn("校验 MP4 可播放性失败，按不可播放处理并重转。原因：{}", e.toString());
          // 继续走 needConvert = true
        }
      }

      if (needConvert) {
        try {
          log.info("开始转换：{} → {}", targetFile, mp4FilePath);
          ProcessResult result = FfmpegUtils.m3u82Mp4(targetFile, mp4FilePath);
          int code = result.getExitCode();

          // 转完再校验一次；任一失败都返回 500
          boolean ok = false;
          if (code == 0 && mp4File.exists()) {
            try {
              ok = FfmpegUtils.isMp4Playable(mp4File);
            } catch (Exception e) {
              log.warn("转码后校验 MP4 失败：{}", e.toString());
            }
          }

          if (!ok) {
            if (mp4File.exists()) {
              // 清理坏文件，避免下次命中坏缓存
              boolean deleted = mp4File.delete();
              log.warn("删除不可播放 MP4：{}，结果：{}", mp4FilePath, deleted);
            }
            response.setStatus(500);
            return response.setBodyString("ffmpeg 转换失败，退出码=" + code);
          }
          log.info("转换完成且通过校验：{}", mp4FilePath);
        } catch (IOException | InterruptedException e) {
          log.error("ffmpeg 转换异常", e);
          response.setStatus(500);
          return response.setBodyString("ffmpeg 转换异常: " + e.getMessage());
        }
      }

      targetFile = mp4FilePath;
      file = new File(targetFile);
      suffix = "mp4";
    }

    // 3) 静态文件响应（带缓存 & Range）
    long fileLength = file.length();
    long lastModified = file.lastModified();

    String etag = HttpFileDataUtils.generateETag(file, lastModified, fileLength);
    String contentType = ContentTypeUtils.getContentType(suffix);
    HttpFileDataUtils.setCacheHeaders(response, lastModified, etag, contentType, suffix);

    if (HttpFileDataUtils.isClientCacheValid(request, lastModified, etag)) {
      response.setStatus(304);
      return response;
    }

    String range = request.getHeader("range");
    if (range != null && range.startsWith("bytes=")) {
      return HttpFileDataUtils.handleRangeRequest(response, file, range, fileLength, contentType);
    } else {
      return HttpFileDataUtils.handleFullFileRequest(response, file, fileLength, contentType);
    }
  }
}
