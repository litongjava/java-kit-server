package com.litongjava.kit.store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.litongjava.kit.vo.HlsSession;

/**
 * 存储 sessionId 和 hlsPtr 的映射，注意线程安全
 */
public class HlsSessionStore {

  // key: sessionId, value: hlsPtr
  private static final ConcurrentMap<Long, HlsSession> SESSION_MAP = new ConcurrentHashMap<>();

  private HlsSessionStore() {
  }

  public static void put(Long sessionId, Long hlsPtr, String hls) {
    if (sessionId != null && hlsPtr != null) {
      SESSION_MAP.put(sessionId, new HlsSession(hls, hlsPtr));
    }
  }

  public static HlsSession get(Long sessionId) {
    if (sessionId == null) {
      return null;
    }
    return SESSION_MAP.get(sessionId);
  }

  public static HlsSession remove(Long sessionId) {
    if (sessionId == null) {
      return null;
    }
    return SESSION_MAP.remove(sessionId);
  }

  // 如果需要，可以增加一个 contains 方法
  public static boolean contains(Long sessionId) {
    return sessionId != null && SESSION_MAP.containsKey(sessionId);
  }
}
