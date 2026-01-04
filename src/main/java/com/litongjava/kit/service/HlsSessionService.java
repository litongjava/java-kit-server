package com.litongjava.kit.service;

import java.io.File;

import com.litongjava.kit.store.HlsSessionStore;
import com.litongjava.kit.vo.HlsSession;
import com.litongjava.media.NativeMedia;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HlsSessionService {

  public void initPersistentHls(Long sessionId, String m3u8Path, String tsPattern, int startNumber, int segmentDuration) {
    long hlsPtr = NativeMedia.initPersistentHls(m3u8Path, tsPattern, startNumber, segmentDuration);
    log.info("add to sessionStore:{},{},{}", sessionId, hlsPtr, m3u8Path);
    HlsSessionStore.put(sessionId, hlsPtr, m3u8Path);
  }

  public void close(Long sessionId) {
    HlsSession hlsSession = HlsSessionStore.get(sessionId);
    if (hlsSession != null && hlsSession.getPrt() != null) {
      Long session_prt = hlsSession.getPrt();
      String m3u8Path = hlsSession.getHls();
      if (m3u8Path != null) {
        File file = new File(m3u8Path);
        if (file.exists()) {
          log.info("finishPersistentHls:{}", session_prt);
          NativeMedia.finishPersistentHls(session_prt, m3u8Path);
        } else {
          log.info("freeHlsSession:{}", session_prt);
          if (session_prt != null) {
            NativeMedia.freeHlsSession(session_prt);
          }
        }
      } else {
        log.info("freeHlsSession:{}", session_prt);
        if (session_prt != null) {
          NativeMedia.freeHlsSession(session_prt);
        }
      }
    }
  }
}
