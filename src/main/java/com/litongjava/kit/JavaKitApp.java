package com.litongjava.kit;

import java.lang.Thread.Builder.OfVirtual;
import java.util.concurrent.ThreadFactory;

import com.litongjava.kit.config.KitAppConfig;
import com.litongjava.tio.boot.TioApplication;
import com.litongjava.tio.boot.server.TioBootServer;

public class JavaKitApp {
  public static void main(String[] args) {
    long start = System.currentTimeMillis();
    // 1. 创建虚拟线程 Builder
    OfVirtual ofVirtual = Thread.ofVirtual();

    // 2. 创建虚拟线程工厂，并指定线程名称前缀
    ThreadFactory factory = ofVirtual.name("tio-v-", 1).factory();

    // 3. 获取 TioBootServer 实例并设置线程工厂
    TioBootServer server = TioBootServer.me();
    server.setThreadFactory(factory);
    
    TioApplication.run(JavaKitApp.class, new KitAppConfig(), args);
    long end = System.currentTimeMillis();
    System.out.println((end - start) + "ms");
  }
}