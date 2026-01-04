package com.litongjava.kit.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import com.jfinal.kit.Kv;
import com.litongjava.kit.vo.HlsSession;
import com.litongjava.media.NativeMedia;
import com.litongjava.model.body.RespBodyVo;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.hutool.FilenameUtils;
import com.litongjava.tio.utils.snowflake.SnowflakeIdUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * HLS 业务处理 Service
 */
@Slf4j
public class HlsService {

  // 内存中存储会话信息，实际场景建议使用持久化存储
  private static ConcurrentHashMap<Long, HlsSession> sessionMap = new ConcurrentHashMap<>();

  /**
   * 会话初始化，生成播放列表 URL 并创建会话记录
   *
   * @param sessionId 客户端传入的会话 ID，如为空则自动生成
   * @param timestamp 时间戳（备用）
   * @return 包含 stream_url 的响应体
   */
  public RespBodyVo startSession(Long sessionId, Long timestamp) {
    if (sessionId == null) {
      sessionId = SnowflakeIdUtils.id();
    }
    if (timestamp == null) {
      timestamp = SystemTimer.currTime;
    }
    String playFilePath = createPlaylistFile(sessionId);
    HlsSession session = new HlsSession(sessionId, playFilePath);
    sessionMap.put(sessionId, session);
    Kv kv = Kv.by("stream_url", playFilePath);
    return RespBodyVo.ok(kv);
  }

  /**
   * 根据 sessionId 创建对应目录，并生成初始的 playlist.m3u8 文件
   *
   * @param sessionId 会话 ID
   */
  public static String createPlaylistFile(Long sessionId) {
    // 拼接目录路径和文件路径，例如: ./data/hls/{sessionId}/playlist.m3u8
    String dirPath = "./data/hls/" + sessionId;
    File dir = new File(dirPath);
    if (!dir.exists()) {
      // 创建目录及其所有父目录
      dir.mkdirs();
    }

    String filePath = dirPath + "/playlist.m3u8";
    File file = new File(filePath);

    // 定义初始播放列表内容，包含 HLS 必须的头信息
    String content = "#EXTM3U\n" + "#EXT-X-VERSION:3\n" + "#EXT-X-TARGETDURATION:10\n" + "#EXT-X-MEDIA-SEQUENCE:0\n";

    // 写入文件
    try (FileWriter writer = new FileWriter(file)) {
      writer.write(content);
      writer.flush();
    } catch (IOException e) {
      log.error("Failed to create playlist file: " + e.getMessage());
      e.printStackTrace();
    }
    return filePath;
  }

  /**
   * 上传场景文件（模拟处理）
   *
   * @param sessionId    会话 ID
   * @param sceneI_index 场景序号
   * @param fileData     文件数据（实际代码中应处理 multipart 文件）
   * @return 返回 session_id 与场景序号
   */
  public RespBodyVo convert(Long sessionId, UploadFile uploadFile) {
    String name = uploadFile.getName();
    String baseName = FilenameUtils.getBaseName(name);
    String subPath = "./data/hls/" + sessionId + "/";

    String relPath = subPath + name;
    File file = new File(relPath);
    file.getParentFile().mkdirs();
    FileUtil.writeBytes(uploadFile.getData(), file);

    String hlsPath = subPath + baseName + ".m3u8";

    String appendMp4ToHLS = NativeMedia.splitVideoToHLS(hlsPath, relPath, subPath + "/" + baseName + "_%03d.ts", 1);
    log.info(appendMp4ToHLS);
    Kv kv = Kv.by("session_id", sessionId);
    return RespBodyVo.ok(kv);
  }

  /**
   * 获取播放列表 URL
   *
   * @param sessionId 会话 ID
   * @return 包含 stream_url 的响应体
   */
  public RespBodyVo getStreamUrl(Long sessionId) {
    if (sessionId == null || !sessionMap.containsKey(sessionId)) {
      return RespBodyVo.fail("Session not found");
    }
    HlsSession session = sessionMap.get(sessionId);
    Kv kv = Kv.by("stream_url", session.getHls());
    return RespBodyVo.ok(kv);
  }

  /**
   * 查询当前处理状态（这里简单返回播放列表 URL，可扩展更多状态信息）
   *
   * @param sessionId 会话 ID
   * @return 状态信息响应体
   */
  public RespBodyVo getStatus(Long sessionId) {
    if (sessionId == null || !sessionMap.containsKey(sessionId)) {
      return RespBodyVo.fail("Session not found");
    }
    HlsSession session = sessionMap.get(sessionId);
    Kv kv = Kv.by("stream_url", session.getHls());
    return RespBodyVo.ok(kv);
  }

  /**
   * 结束会话，模拟在播放列表中追加 EXT‑X‑ENDLIST 标签
   *
   * @param sessionId 会话 ID
   * @param finishTime 结束时间戳（备用）
   * @return 结束响应体
   */
  public RespBodyVo finishSession(Long sessionId, Long finishTime) {
    if (sessionId == null || !sessionMap.containsKey(sessionId)) {
      return RespBodyVo.fail("Session not found");
    }
    HlsSession session = sessionMap.get(sessionId);
    session.setFinished(true);
    String playlistUrl = session.getHls();

    // 将 #EXT-X-ENDLIST 写入文件
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(playlistUrl, true))) {
      writer.write("#EXT-X-ENDLIST");
      writer.newLine();
    } catch (IOException e) {
      e.printStackTrace();
      return RespBodyVo.fail("Failed to write #EXT-X-ENDLIST to file: " + e.getMessage());
    }
    return RespBodyVo.ok();
  }

}
