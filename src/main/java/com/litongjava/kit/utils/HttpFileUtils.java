package com.litongjava.kit.utils;

import java.io.File;
import java.io.IOException;

import com.google.common.io.Files;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.result.OkResult;
import com.litongjava.tio.utils.http.HttpUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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

  @SuppressWarnings("unchecked")
  public static OkResult<String> download(String url) {
    String path = getLocalPath(url);

    String targetFile = "downloads" + File.separator + path;

    File to = new File(targetFile);
    if (!to.exists()) {
      ResponseVo responseVo = HttpUtils.download(url);
      if (!responseVo.isOk()) {
        return OkResult.fail("Failed to downlaod file");
      }

      File parentFile = to.getParentFile();
      if (!parentFile.exists()) {
        parentFile.mkdirs();
      }

      try {
        Files.write(responseVo.getBodyBytes(), to);
      } catch (IOException e) {
        log.error(e.getMessage(), e);
        return OkResult.exception(e);
      }
      return OkResult.ok(targetFile);
    } else {
      return OkResult.ok(targetFile);
    }
  }
}
