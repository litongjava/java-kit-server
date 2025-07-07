package com.litongjava.kit;

import com.litongjava.kit.config.AppConfig;
import com.litongjava.tio.boot.TioApplication;

public class JavaKitApp {
  public static void main(String[] args) {
    long start = System.currentTimeMillis();
    TioApplication.run(JavaKitApp.class, new AppConfig(), args);
    long end = System.currentTimeMillis();
    System.out.println((end - start) + "ms");
  }
}