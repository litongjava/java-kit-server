package com.litongjava.kit.handler;

import java.io.File;
import java.io.IOException;

import com.litongjava.kit.utils.FfmpegUtils; // 导入
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.boot.utils.HttpFileDataUtils;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.CORSUtils;
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

    // 1 ️获取目标文件
    String targetFile = "." + path;
    File file = new File(targetFile);
    if (!file.exists()) {
      response.setStatus(404);
      return response;
    }

    // 2️ 如果是 m3u8 并且 mp4 不存在，则转换
    String suffix = FilenameUtils.getSuffix(targetFile);
    if ("m3u8".equalsIgnoreCase(suffix)) {
      String subPath = FilenameUtils.getSubPath(targetFile);
      String baseName = FilenameUtils.getBaseName(targetFile);
      String mp4FilePath = subPath + File.separator + baseName + ".mp4";
      File mp4File = new File(mp4FilePath);

      if (!mp4File.exists()) {
        try {
          log.info("检测到 m3u8 文件，开始转换：{} → {}", targetFile, mp4FilePath);
          int code = FfmpegUtils.m3u82Mp4(targetFile, mp4FilePath);
          if (code != 0 || !mp4File.exists()) {
            response.setStatus(500);
            return response.setBodyString("ffmpeg 转换失败，退出码=" + code);
          }
          log.info("转换完成：{}", mp4FilePath);
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
    
    // 生成 ETag
    long fileLength = file.length();
    long lastModified = file.lastModified();

    String etag = HttpFileDataUtils.generateETag(file, lastModified, fileLength);

    // 设置缓存相关头部
    String contentType = ContentTypeUtils.getContentType(suffix);
    HttpFileDataUtils.setCacheHeaders(response, lastModified, etag, contentType, suffix);

    // 检查客户端缓存
    if (HttpFileDataUtils.isClientCacheValid(request, lastModified, etag)) {
      response.setStatus(304); // Not Modified
      return response;
    }

    // 检查是否存在 Range 头信息
    String range = request.getHeader("range");
    if (range != null && range.startsWith("bytes=")) {
      return HttpFileDataUtils.handleRangeRequest(response, file, range, fileLength, contentType);
    } else {
      return HttpFileDataUtils.handleFullFileRequest(response, file, fileLength, contentType);
    }
  }
}
