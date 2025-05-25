package com.litongjava.linux.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.ResponseHeaderKey;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.http.server.util.Resps;
import com.litongjava.tio.utils.http.ContentTypeUtils;
import com.litongjava.tio.utils.hutool.FilenameUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataHandler {

  // HTTP 日期格式（线程安全）
  private static final DateTimeFormatter HTTP_DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US).withZone(ZoneId.of("GMT"));

  public HttpResponse index(HttpRequest request) {
    String path = request.getRequestLine().getPath();
    log.info(path);
    HttpResponse response = TioRequestContext.getResponse();
    CORSUtils.enableCORS(response);

    File file = new File("." + File.separator + path);
    String suffix = FilenameUtils.getSuffix(path);
    String contentType = ContentTypeUtils.getContentType(suffix);

    if (!file.exists()) {
      response.setStatus(404);
      return response;
    }

    long fileLength = file.length();
    long lastModified = file.lastModified();

    // 生成 ETag
    String etag = generateETag(file, lastModified, fileLength);

    // 设置缓存相关头部
    setCacheHeaders(response, lastModified, etag, contentType);

    // 检查客户端缓存
    if (isClientCacheValid(request, lastModified, etag)) {
      response.setStatus(304); // Not Modified
      return response;
    }

    // 检查是否存在 Range 头信息
    String range = request.getHeader("Range");
    if (range != null && range.startsWith("bytes=")) {
      return handleRangeRequest(response, file, range, fileLength, contentType);
    } else {
      return handleFullFileRequest(response, file, fileLength, contentType);
    }
  }

  private void setCacheHeaders(HttpResponse response, long lastModified, String etag, String contentType) {
    // 设置 Last-Modified
    String lastModStr = HTTP_DATE_FORMAT.format(Instant.ofEpochMilli(lastModified));
    response.setHeader("Last-Modified", lastModStr);

    // 设置 ETag
    response.setHeader("ETag", etag);

    // 设置 Cache-Control - 根据文件类型设置不同的缓存策略
    String cacheControl = getCacheControlForContentType(contentType);
    response.setHeader("Cache-Control", cacheControl);

    // 设置 Expires (1年后过期，适用于静态资源)
    long expiresTime = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000);
    String expiresStr = HTTP_DATE_FORMAT.format(Instant.ofEpochMilli(expiresTime));
    response.setHeader("Expires", expiresStr);

    // Cloudflare 特定头部
    response.setHeader("CF-Cache-Status", "MISS");

    // 设置 Vary 头部
    response.setHeader("Vary", "Accept-Encoding");
  }

  private boolean isClientCacheValid(HttpRequest request, long lastModified, String etag) {
    // 检查 If-None-Match (ETag)
    String ifNoneMatch = request.getHeader("If-None-Match");
    if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
      return true;
    }

    // 检查 If-Modified-Since
    String ifModifiedSince = request.getHeader("If-Modified-Since");
    if (ifModifiedSince != null) {
      try {
        ZonedDateTime clientDate = ZonedDateTime.parse(ifModifiedSince, HTTP_DATE_FORMAT);
        Instant fileInstant = Instant.ofEpochMilli(lastModified);
        if (!fileInstant.isAfter(clientDate.toInstant())) {
          return true;
        }
      } catch (Exception e) {
        // 解析失败，忽略
      }
    }

    return false;
  }

  private String getCacheControlForContentType(String contentType) {
    if (contentType == null) {
      return "public, max-age=3600";
    }
    if (contentType.startsWith("image/")) {
      return "public, max-age=31536000, immutable";
    } else if (contentType.startsWith("video/") || contentType.startsWith("audio/")) {
      return "public, max-age=31536000, immutable";
    } else if (contentType.equals("text/css") || contentType.equals("application/javascript")) {
      return "public, max-age=31536000, immutable";
    } else if (contentType.startsWith("font/") || contentType.equals("application/font-woff") || contentType.equals("application/font-woff2")) {
      return "public, max-age=31536000, immutable";
    } else if (contentType.startsWith("text/")) {
      return "public, max-age=3600";
    } else {
      return "public, max-age=86400";
    }
  }

  private String generateETag(File file, long lastModified, long fileLength) {
    try {
      String input = file.getAbsolutePath() + fileLength + lastModified;
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] hash = md.digest(input.getBytes());

      StringBuilder sb = new StringBuilder();
      sb.append('"');
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      sb.append('"');
      return sb.toString();
    } catch (Exception e) {
      return "\"" + lastModified + "-" + fileLength + "\"";
    }
  }

  private HttpResponse handleRangeRequest(HttpResponse response, File file, String range, long fileLength, String contentType) {
    String rangeValue = range.substring("bytes=".length());
    String[] parts = rangeValue.split("-");

    try {
      long start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
      long end = (parts.length > 1 && !parts[1].isEmpty()) ? Long.parseLong(parts[1]) : fileLength - 1;

      if (start > end || end >= fileLength) {
        response.setStatus(416);
        response.setHeader("Content-Range", "bytes */" + fileLength);
        return response;
      }

      long contentLength = end - start + 1;
      byte[] data = readFileRange(file, start, contentLength);
      if (data == null) {
        response.setStatus(500);
        return response;
      }

      response.setStatus(206);
      response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
      response.setHeader("Accept-Ranges", "bytes");
      response.setHeader(ResponseHeaderKey.Content_Length, String.valueOf(contentLength));
      Resps.bytesWithContentType(response, data, contentType);
    } catch (Exception e) {
      response.setStatus(416);
      response.setHeader("Content-Range", "bytes */" + fileLength);
    }

    response.setHasGzipped(false);
    return response;
  }

  private HttpResponse handleFullFileRequest(HttpResponse response, File file, long fileLength, String contentType) {
    byte[] fileData = readFullFile(file);
    if (fileData == null) {
      response.setStatus(500);
      return response;
    }

    response.setHeader("Accept-Ranges", "bytes");
    response.setHeader(ResponseHeaderKey.Content_Length, String.valueOf(fileLength));
    Resps.bytesWithContentType(response, fileData, contentType);

    if (contentType != null && (contentType.startsWith("video/") || contentType.startsWith("audio/"))) {
      response.setHasGzipped(true);
    } else {
      response.setHasGzipped(false);
    }

    return response;
  }

  private byte[] readFileRange(File file, long start, long length) {
    try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {

      ByteBuffer buffer = ByteBuffer.allocate((int) length);
      channel.position(start);

      int bytesRead = 0;
      while (bytesRead < length) {
        int read = channel.read(buffer);
        if (read == -1)
          break;
        bytesRead += read;
      }
      return buffer.array();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  private byte[] readFullFile(File file) {
    try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {

      long fileSize = channel.size();
      ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
      while (buffer.hasRemaining()) {
        if (channel.read(buffer) == -1)
          break;
      }
      return buffer.array();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }
}