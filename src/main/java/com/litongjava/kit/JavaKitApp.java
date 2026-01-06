package com.litongjava.kit;

import java.lang.Thread.Builder.OfVirtual;
import java.util.concurrent.ThreadFactory;

import com.litongjava.kit.config.KitAppConfig;
import com.litongjava.tio.boot.TioApplication;
import com.litongjava.tio.boot.server.TioBootServer;

public class JavaKitApp {
  public static void main(String[] args) {
    long start = System.currentTimeMillis();
    OfVirtual ofVirtual = Thread.ofVirtual();

    ThreadFactory factory = ofVirtual.name("t-io-v-", 1).factory();

    TioBootServer server = TioBootServer.me();
    server.setWorkThreadFactory(factory);

    TioApplication.run(JavaKitApp.class, new KitAppConfig(), args);
    long end = System.currentTimeMillis();
    System.out.println((end - start) + "ms");
  }
}