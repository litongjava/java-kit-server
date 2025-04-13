package com.litongjava.linux.service;

import org.junit.Test;

import com.litongjava.media.NativeMedia;

public class GetVideoLegthTest {

  @Test
  public void testGetVideoLength() {
    String file="main.mp4";
    double videoLength = NativeMedia.getVideoLength(file);
    System.out.println(videoLength);
  }
}
