package com.litongjava.kit.service;

import org.junit.Test;

import com.litongjava.tio.utils.environment.EnvUtils;

public class AiSearchServiceTest {

  @Test
  public void test() {
    EnvUtils.load();
    long start = System.currentTimeMillis();
    new GoogleGeminiSearchService().search("Claude 4.5 发布时间");
    long end = System.currentTimeMillis();
    System.out.println(end-start);
  }

}
