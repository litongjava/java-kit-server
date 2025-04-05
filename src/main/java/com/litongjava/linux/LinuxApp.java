package com.litongjava.linux;

import com.litongjava.linux.config.AppConfig;
import com.litongjava.tio.boot.TioApplication;

public class LinuxApp {
  public static void main(String[] args) {
    long start = System.currentTimeMillis();
    TioApplication.run(LinuxApp.class, new AppConfig(), args);
    long end = System.currentTimeMillis();
    System.out.println((end - start) + "ms");
  }
}