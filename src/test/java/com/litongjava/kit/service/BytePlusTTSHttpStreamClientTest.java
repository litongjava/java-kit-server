package com.litongjava.kit.service;

import com.litongjava.byteplus.BytePlusTTSHttpStreamClient;
import com.litongjava.byteplus.BytePlusVoice;
import com.litongjava.tio.utils.environment.EnvUtils;

public class BytePlusTTSHttpStreamClientTest {
  public static void main(String[] args) {
    EnvUtils.load();
    String input = "Welcome to use ByteDance text-to-speech services 111";
    BytePlusTTSHttpStreamClient client = new BytePlusTTSHttpStreamClient();
    client.tts(input, BytePlusVoice.zh_female_cancan_mars_bigtts);
  }
}
