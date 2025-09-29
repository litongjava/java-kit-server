package com.litongjava.kit.handler;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.google.common.io.Files;
import com.litongjava.kit.utils.HttpFileUtils;
import com.litongjava.media.utils.VideoWaterUtils;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.http.server.util.Resps;
import com.litongjava.tio.utils.crypto.Md5Utils;
import com.litongjava.tio.utils.http.ContentTypeUtils;
import com.litongjava.tio.utils.http.HttpUtils;
import com.litongjava.tio.utils.hutool.FilenameUtils;
import com.litongjava.tio.utils.hutool.StrUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VideoWaterHandler {

  public HttpResponse index(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);
    String path = request.getString("path");
    String text = request.getString("text");
    String filename = request.getString("filename");
    if (StrUtil.isNotBlank(filename)) {
      filename += ".mp4";
    } else {
      filename = "main.mp4";
    }
    Integer fontSize = request.getInt("font_size");
    if (fontSize == null) {
      fontSize = 24;
    }

    if (StrUtil.isBlank(path)) {
      return response.body("path can not be empty");
    }

    String targetFile = "." + path;
    File file = new File(targetFile);
    if (!file.exists()) {
      response.setStatus(404);
      return response;
    }

    String suffix = FilenameUtils.getSuffix(path);
    String contentType = ContentTypeUtils.getContentType(suffix);

    if (StrUtil.isNotBlank(text)) {
      String md5 = Md5Utils.md5Hex(text);
      String subPath = FilenameUtils.getSubPath(targetFile);
      String baseName = FilenameUtils.getBaseName(targetFile);
      String outputFile = subPath + File.separator + baseName + "_" + fontSize + "_" + md5 + "." + suffix;
      file = new File(outputFile);
      if (!file.exists()) {
        try {
          VideoWaterUtils.addWatermark(targetFile, outputFile, fontSize, text);
          targetFile = outputFile;
        } catch (IOException e) {
          e.printStackTrace();
          return response.body(e.getMessage());
        } catch (InterruptedException e) {
          e.printStackTrace();
          return response.body(e.getMessage());
        }
      }
    }

    long fileLength = file.length();
    // 检查是否存在 Range 头信息
    String range = request.getHeader("range");
    if (range != null && range.startsWith("bytes=")) {
      String rangeValue = range.substring("bytes=".length());
      String[] parts = rangeValue.split("-");
      try {
        long start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
        long end = (parts.length > 1 && !parts[1].isEmpty()) ? Long.parseLong(parts[1]) : fileLength - 1;
        if (start > end || end >= fileLength) {
          response.setStatus(416);
          return response;
        }
        long contentLength = end - start + 1;
        byte[] data = new byte[(int) contentLength];

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
          raf.seek(start);
          raf.readFully(data);
        }
        // 设置响应头
        response.setStatus(206); // Partial Content
        response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
        response.setHeader("Accept-Ranges", "bytes");
        // 如果传入了 filename，则在响应头中指定下载文件名
        if (StrUtil.isNotBlank(filename)) {
          response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        }
        Resps.bytesWithContentType(response, data, contentType);
      } catch (Exception e) {
        response.setStatus(416);
      }
    } else {
      response.setHeader("Accept-Ranges", "bytes");
      if (StrUtil.isNotBlank(filename)) {
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
      }
      response.setFileBody(file);
    }
    // 视频文件（如 mp4）本身已经是压缩格式，再进行 gzip 压缩可能会破坏文件格式，导致浏览器无法正确解码。
    response.setSkipGzipped(true);
    return response;
  }

  public HttpResponse exists(HttpRequest request) {
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);
    String path = request.getString("path");
    log.info("path:{}", path);
    if (StrUtil.isBlank(path)) {
      return response.body("path can not be empty");
    }

    String targetFile = null;
    if (path.startsWith("http")) {
      String url = path;
      path = HttpFileUtils.getLocalPath(path);
      ResponseVo responseVo = HttpUtils.download(url);
      if (!responseVo.isOk()) {
        return response.body(RespBodyVo.fail("file is not exists"));
      }

      targetFile = "downloads" + File.separator + path;

      try {
        File to = new File(targetFile);
        File parentFile = to.getParentFile();
        if (!parentFile.exists()) {
          parentFile.mkdirs();
        }
        Files.write(responseVo.getBodyBytes(), to);
      } catch (IOException e) {
        log.error(e.getMessage(), e);
        return response.body(RespBodyVo.fail(e.getMessage()));
      }
    } else {
      targetFile = "." + path;
    }

    File file = new File(targetFile);
    if (!file.exists()) {
      return response.body(RespBodyVo.fail("file is not exists"));
    }
    return response.body(RespBodyVo.ok());
  }
}
