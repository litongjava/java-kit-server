package com.litongjava.kit.service.pptx;

import java.io.File;
import java.io.IOException;

import com.litongjava.kit.utils.PptUtils;
import com.litongjava.tio.utils.hutool.FilenameUtils;

public class ImagesToPptx {

  public static void main(String[] args) throws IOException {

    // 1. 图片所在目录
    File dir = new File("E:\\code\\java\\project-litongjava\\java-kit-server\\data\\scenes\\594548639856791552"); // 换成你的路径

    File[] imageFiles = dir.listFiles(f -> f.isFile() && FilenameUtils.isImageFile(f.getName()));

    PptUtils.addImage(imageFiles, "output.pptx");
  }

}