package com.litongjava.kit.utils;

public class HttpFileUtils {

  public static String getLocalPath(String url) {
    if (url == null) {
      return null;
    }
    if (url.startsWith("https://")) {
      return url.replace("https:/", "");
    } else if (url.startsWith("http://")) {
      return url.replace("http:/", "");
    }
    return url;
  }
}
