package com.litongjava.linux.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.litongjava.media.NativeMedia;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VideoMergeTest {

  @Test
  public void testSession() {

    String folderPath = "C:\\Users\\Administrator\\Downloads";
    File folderFile = new File(folderPath);
    File[] listFiles = folderFile.listFiles();

    // 使用 ArrayList 来存储符合条件的文件路径
    List<String> videoPaths = new ArrayList<>();
    if (listFiles != null) {
      for (File file : listFiles) {
        if (file != null && file.getName().endsWith(".mp4")) {
          videoPaths.add(file.getAbsolutePath());
        }
      }
    }

    // 如果 NativeMedia.merge 方法需要数组，可以通过 toArray 方法转换
    NativeMedia.merge(videoPaths.toArray(new String[0]), "main.mp4");
  }
}
