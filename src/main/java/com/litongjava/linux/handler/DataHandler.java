package com.litongjava.linux.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.ResponseHeaderKey;
import com.litongjava.tio.http.server.util.CORSUtils;
import com.litongjava.tio.http.server.util.Resps;
import com.litongjava.tio.utils.http.ContentTypeUtils;
import com.litongjava.tio.utils.hutool.FilenameUtils;

public class DataHandler {

  // 简单的文件缓存，生产环境建议使用更专业的缓存框架
  private static final ConcurrentHashMap<String, CacheEntry> fileCache = new ConcurrentHashMap<>();
  private static final long CACHE_EXPIRE_TIME = TimeUnit.MINUTES.toMillis(10); // 10分钟过期
  private static final int MAX_CACHE_SIZE = 100; // 最大缓存文件数
  private static final long MAX_CACHEABLE_FILE_SIZE = 10 * 1024 * 1024; // 10MB以下才缓存

  static class CacheEntry {
    final byte[] data;
    final long lastModified;
    final long cacheTime;

    CacheEntry(byte[] data, long lastModified) {
      this.data = data;
      this.lastModified = lastModified;
      this.cacheTime = System.currentTimeMillis();
    }

    boolean isExpired() {
      return System.currentTimeMillis() - cacheTime > CACHE_EXPIRE_TIME;
    }
  }

  public HttpResponse index(HttpRequest request) {
    String path = request.getRequestLine().getPath();
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

    // 检查是否存在 Range 头信息
    String range = request.getHeader("Range");
    if (range != null && range.startsWith("bytes=")) {
      return handleRangeRequest(response, file, range, fileLength, contentType);
    } else {
      return handleFullFileRequest(response, file, path, fileLength, lastModified, contentType);
    }
  }

  private HttpResponse handleRangeRequest(HttpResponse response, File file, String range, long fileLength, String contentType) {
    // 例如 Range: bytes=0-1023
    String rangeValue = range.substring("bytes=".length());
    String[] parts = rangeValue.split("-");

    try {
      long start = parts[0].isEmpty() ? 0 : Long.parseLong(parts[0]);
      long end = (parts.length > 1 && !parts[1].isEmpty()) ? Long.parseLong(parts[1]) : fileLength - 1;

      // 检查 range 合法性
      if (start > end || end >= fileLength) {
        response.setStatus(416); // Range Not Satisfiable
        return response;
      }

      long contentLength = end - start + 1;

      // 使用 FileChannel 进行更高效的文件读取
      byte[] data = readFileRange(file, start, contentLength);
      if (data == null) {
        response.setStatus(500);
        return response;
      }

      // 设置响应头
      response.setStatus(206); // Partial Content
      response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
      response.setHeader("Accept-Ranges", "bytes");
      response.setHeader(ResponseHeaderKey.Content_Length, String.valueOf(contentLength));
      Resps.bytesWithContentType(response, data, contentType);

    } catch (Exception e) {
      response.setStatus(416);
    }

    response.setHasGzipped(true);
    return response;
  }

  private HttpResponse handleFullFileRequest(HttpResponse response, File file, String path, long fileLength, long lastModified, String contentType) {
    byte[] fileData = null;

    // 尝试从缓存获取（仅对小文件进行缓存）
    if (fileLength <= MAX_CACHEABLE_FILE_SIZE) {
      fileData = getFromCache(path, lastModified);
    }

    // 缓存未命中，读取文件
    if (fileData == null) {
      fileData = readFullFile(file);
      if (fileData == null) {
        response.setStatus(500);
        return response;
      }

      // 将小文件放入缓存
      if (fileLength <= MAX_CACHEABLE_FILE_SIZE) {
        putToCache(path, fileData, lastModified);
      }
    }

    response.setHeader("Accept-Ranges", "bytes");
    response.setHeader(ResponseHeaderKey.Content_Length, String.valueOf(fileLength));
    Resps.bytesWithContentType(response, fileData, contentType);
    response.setHasGzipped(true);
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

  private byte[] getFromCache(String path, long lastModified) {
    CacheEntry entry = fileCache.get(path);
    if (entry != null && !entry.isExpired() && entry.lastModified == lastModified) {
      return entry.data;
    }
    return null;
  }

  private void putToCache(String path, byte[] data, long lastModified) {
    // 简单的缓存大小控制
    if (fileCache.size() >= MAX_CACHE_SIZE) {
      // 清理过期缓存
      fileCache.entrySet().removeIf(entry -> entry.getValue().isExpired());

      // 如果还是太大，清理一些旧的缓存
      if (fileCache.size() >= MAX_CACHE_SIZE) {
        fileCache.clear(); // 简单粗暴的清理策略
      }
    }

    fileCache.put(path, new CacheEntry(data, lastModified));
  }
}