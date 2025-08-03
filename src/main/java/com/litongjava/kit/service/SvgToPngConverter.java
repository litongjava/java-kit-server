package com.litongjava.kit.service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

public class SvgToPngConverter {

  /**
   * 将SVG文件转换为PNG文件
   * 
   * @param svgPath SVG文件路径
   * @param pngPath 输出PNG文件路径
   * @param width   输出宽度（像素），-1表示保持原始比例
   * @param height  输出高度（像素），-1表示保持原始比例
   */
  public static void convertSvgToPng(String svgPath, String pngPath, float width, float height) throws Exception {
    // 创建PNG转码器
    PNGTranscoder transcoder = new PNGTranscoder();

    // 设置输出尺寸（可选）
    if (width > 0) {
      transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, width);
    }
    if (height > 0) {
      transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height);
    }

    // 设置输入源
    InputStream inputStream = new FileInputStream(svgPath);
    TranscoderInput input = new TranscoderInput(inputStream);

    // 设置输出目标
    OutputStream outputStream = new FileOutputStream(pngPath);
    TranscoderOutput output = new TranscoderOutput(outputStream);

    // 执行转换
    transcoder.transcode(input, output);

    // 关闭流
    outputStream.flush();
    outputStream.close();
    inputStream.close();
  }

  public static void main(String[] args) {
    try {
      // 示例：保持原始尺寸转换
      String svgPath = "F:\\code\\rust\\project-litongjava\\tauri-glm\\icons\\src.svg";
      String pngPath = "F:\\code\\rust\\project-litongjava\\tauri-glm\\icons\\src.png";
      //convertSvgToPng(svgPath, pngPath, -1, -1);
      convertSvgToPng(svgPath, pngPath, 1024,1024);

      System.out.println("转换成功！");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}