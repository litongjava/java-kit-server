package com.litongjava.kit.utils;

import java.io.File;

public class FolderUtils {

  public static final String hls(Long sessionId) {
    return "data" + File.separator + "hls" + File.separator + sessionId;
  }

  public static final String scenes(Long sessionId) {
    return "data" + File.separator + "scenes" + File.separator + sessionId;
  }

  public static final String combined(Long sessionId) {
    return "data" + File.separator + "combined" + File.separator + sessionId;
  }

  public static final String httpM3u8(Long sessionId, String name) {
    return "/data/hls/" + sessionId + "/" + name;
  }

  public static final String httpScenes(Long sessionId, String name) {
    return "/data/scenes/" + sessionId + "/" + name;
  }

}
