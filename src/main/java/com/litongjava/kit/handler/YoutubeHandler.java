package com.litongjava.kit.handler;

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

  public HttpResponse downloadMp3(HttpRequest request) throws IOException, InterruptedException {
    HttpResponse httpResponse = TioRequestContext.getResponse();

    String url = request.getParam("url");
    log.info("download:{}", url);
    String id = YouTubeIdUtil.extractVideoId(url);
    ProcessResult processResult = YtDlpUtils.downloadMp3(id, true);
    File file = processResult.getFile();

    if (file.exists()) {
      String downloadFilename = file.getName();
      String suffix = FilenameUtils.getSuffix(downloadFilename);
      // 设置响应内容类型，此处使用 Markdown 格式
      String contentType = ContentTypeUtils.getContentType(suffix);
      log.info("filename:{},{}", downloadFilename, contentType);
      httpResponse.setContentType(contentType);
      httpResponse.setAttachmentFilename(downloadFilename);
      httpResponse.setFileBody(file);
    } else {
      return httpResponse.error("File not found");
    }
    return httpResponse;
  }
}
