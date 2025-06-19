package com.litongjava.linux.handler;

import java.io.File;

import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.utils.http.ContentTypeUtils;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.hutool.FilenameUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DownloadHandler {

  public HttpResponse donwload(HttpRequest httpRequest) {

    HttpResponse httpResponse = TioRequestContext.getResponse();
    String downloadFilename = "readme.md";
    File file = new File(downloadFilename);

    String suffix = FilenameUtils.getSuffix(downloadFilename);
    // 设置响应内容类型，此处使用 Markdown 格式
    String contentType = ContentTypeUtils.getContentType(suffix);
    log.info("filename:{},{}", downloadFilename, contentType);
    
    
    // 设置 Content-Type 响应头，确保浏览器以正确格式解析文件内容
    httpResponse.setContentType(contentType);
    // 设置 Content-Disposition 响应头，告知浏览器将内容作为附件下载，并指定默认文件名
    httpResponse.setAttachmentFilename(downloadFilename);
    // 设置响应体内容，实际项目中可替换为文件的字节流
    httpResponse.setBody(FileUtil.readBytes(file));

    return httpResponse;
  }
}
