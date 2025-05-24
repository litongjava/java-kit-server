package com.litongjava.linux.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.ResponseHeaderKey;
import com.litongjava.tio.http.server.util.Resps;
import com.litongjava.tio.utils.http.ContentTypeUtils;
import com.litongjava.tio.utils.hutool.FilenameUtils;

public class DataHandler {

  private static final SimpleDateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
  static {
    HTTP_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  public HttpResponse index(HttpRequest request) {
    String path = request.getRequestLine().getPath();
    HttpResponse response = TioRequestContext.getResponse();

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

    // 检查是否为静态资源请求，决定是否启用 CORS
    boolean enableCORS = needsCORS(request, contentType);

    // 设置缓存相关头部
    setCacheHeaders(response, lastModified, etag, contentType, enableCORS);

    // 检查客户端缓存
    if (isClientCacheValid(request, lastModified, etag)) {
      response.setStatus(304);
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

  private boolean needsCORS(HttpRequest request, String contentType) {
    // 检查是否为跨域请求
    String origin = request.getHeader("Origin");
    String referer = request.getHeader("Referer");

    // 如果有 Origin 头或者是媒体文件，才启用 CORS
    return origin != null || (contentType != null && (contentType.startsWith("video/") || contentType.startsWith("audio/") || contentType.startsWith("image/")));
  }

  private void setCacheHeaders(HttpResponse response, long lastModified, String etag, String contentType, boolean enableCORS) {

    // 设置基本缓存头部
    response.setHeader("Last-Modified", HTTP_DATE_FORMAT.format(new Date(lastModified)));
    response.setHeader("ETag", etag);

    // 设置强缓存
    String cacheControl = getCacheControlForContentType(contentType);
    response.setHeader("Cache-Control", cacheControl);

    // 设置 Expires
    long expiresTime = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000);
    response.setHeader("Expires", HTTP_DATE_FORMAT.format(new Date(expiresTime)));

    // 只在需要时设置 CORS 头部
    if (enableCORS) {
      response.setHeader("Access-Control-Allow-Origin", "*");
      response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
      response.setHeader("Access-Control-Max-Age", "86400");
      // 简化 Vary 头部
      response.setHeader("Vary", "Origin");
    } else {
      // 对于纯静态文件，不设置 CORS 头部，只设置基本的 Vary
      response.setHeader("Vary", "Accept-Encoding");
    }

    // Accept-Ranges 对所有文件都设置
    response.setHeader("Accept-Ranges", "bytes");

    // 添加 Cloudflare 缓存提示头部
    response.setHeader("X-Content-Type-Options", "nosniff");

    // 对于媒体文件，添加额外的缓存友好头部
    if (contentType != null && (contentType.startsWith("video/") || contentType.startsWith("audio/") || contentType.startsWith("image/"))) {
      response.setHeader("X-Robots-Tag", "noindex");
    }
  }

  private String getCacheControlForContentType(String contentType) {
    if (contentType == null) {
      return "public, max-age=86400"; // 1天
    }

    if (contentType.startsWith("image/")) {
      return "public, max-age=31536000, immutable"; // 图片1年
    } else if (contentType.startsWith("video/") || contentType.startsWith("audio/")) {
      return "public, max-age=31536000, immutable"; // 媒体文件1年
    } else if (contentType.equals("text/css") || contentType.equals("application/javascript")) {
      return "public, max-age=31536000, immutable"; // CSS/JS 1年
    } else if (contentType.startsWith("font/")) {
      return "public, max-age=31536000, immutable"; // 字体1年
    } else {
      return "public, max-age=86400"; // 其他文件1天
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

  private boolean isClientCacheValid(HttpRequest request, long lastModified, String etag) {
    String ifNoneMatch = request.getHeader("If-None-Match");
    if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
      return true;
    }

    String ifModifiedSince = request.getHeader("If-Modified-Since");
    if (ifModifiedSince != null) {
      try {
        Date clientDate = HTTP_DATE_FORMAT.parse(ifModifiedSince);
        Date fileDate = new Date(lastModified);
        if (!fileDate.after(clientDate)) {
          return true;
        }
      } catch (Exception e) {
        // 忽略解析错误
      }
    }

    return false;
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
      response.setHeader(ResponseHeaderKey.Content_Length, String.valueOf(contentLength));
      Resps.bytesWithContentType(response, data, contentType);

    } catch (Exception e) {
      response.setStatus(416);
      response.setHeader("Content-Range", "bytes */" + fileLength);
    }

    response.setHasGzipped(true); // 标记为已处理压缩
    return response;
  }

  private HttpResponse handleFullFileRequest(HttpResponse response, File file, long fileLength, String contentType) {
    byte[] fileData = readFullFile(file);
    if (fileData == null) {
      response.setStatus(500);
      return response;
    }

    response.setHeader(ResponseHeaderKey.Content_Length, String.valueOf(fileLength));
    Resps.bytesWithContentType(response, fileData, contentType);

    // 媒体文件不压缩
    response.setHasGzipped(true);
    return response;
  }

  private byte[] readFileRange(File file, long start, long length) {
    try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {

      ByteBuffer buffer = ByteBuffer.allocate((int) length);
      channel.position(start);

      int bytesRead = 0;
      while (bytesRead < length && buffer.hasRemaining()) {
        int read = channel.read(buffer);
        if (read == -1)
          break;
        bytesRead += read;
      }

      byte[] result = new byte[bytesRead];
      buffer.flip();
      buffer.get(result);
      return result;

    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  private byte[] readFullFile(File file) {
    try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {

      long fileSize = channel.size();
      if (fileSize > Integer.MAX_VALUE) {
        throw new IOException("File too large");
      }

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