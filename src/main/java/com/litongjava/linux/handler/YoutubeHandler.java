package com.litongjava.linux.handler;

import java.io.File;
import java.io.IOException;

import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.http.ContentTypeUtils;
import com.litongjava.tio.utils.hutool.FilenameUtils;
import com.litongjava.tio.utils.youtube.YouTubeIdUtil;
import com.litongjava.yt.utils.YtDlpUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YoutubeHandler {

  public HttpResponse downloadMp3(HttpRequest request) {
    HttpResponse httpResponse = TioRequestContext.getResponse();

    String url = request.getParam("url");
    log.info("download:{}", url);
    String id = YouTubeIdUtil.extractVideoId(url);
    ProcessResult result = null;
    try {
      result = YtDlpUtils.downloadMp3(id, true);
    } catch (IOException e) {
      e.printStackTrace();
      return httpResponse.error(e.getMessage());
    } catch (InterruptedException e) {
      e.printStackTrace();
      return httpResponse.error(e.getMessage());
    }

    if (result.getFile() != null) {
      File file = result.getFile();
      if (file.exists()) {
        String downloadFilename = file.getName();
        String suffix = FilenameUtils.getSuffix(downloadFilename);
        // 设置响应内容类型，此处使用 Markdown 格式
        String contentType = ContentTypeUtils.getContentType(suffix);
        log.info("filename:{},{}", downloadFilename, contentType);
        httpResponse.setContentType(contentType);
        httpResponse.setAttachmentFilename(downloadFilename);
        httpResponse.setFileBody(file);
      }
    } else {
      return httpResponse.error(result.getStdErr());
    }
    return httpResponse;
  }
}
