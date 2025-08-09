package com.litongjava.kit.service;

import java.awt.Color;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

public class SvgToPngConverter {

  /**
   * 将SVG文件转换为RGBA格式的PNG文件
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

    // 强制设置透明背景 - 确保RGBA输出
    transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, new Color(0, 0, 0, 0));

    // 设置输出格式相关参数
    transcoder.addTranscodingHint(PNGTranscoder.KEY_FORCE_TRANSPARENT_WHITE, Boolean.FALSE);

    // 尝试设置颜色模式（如果Batik版本支持）
    try {
      // 这个参数可能在某些Batik版本中不存在，所以用try-catch包围
      transcoder.addTranscodingHint(PNGTranscoder.KEY_INDEXED, Boolean.FALSE);
    } catch (Exception e) {
      // 忽略不支持的参数
    }

    // 设置输入源
    try (InputStream inputStream = new FileInputStream(svgPath);
        OutputStream outputStream = new FileOutputStream(pngPath)) {

      TranscoderInput input = new TranscoderInput(inputStream);
      TranscoderOutput output = new TranscoderOutput(outputStream);

      // 执行转换
      transcoder.transcode(input, output);

      // 确保数据写入
      outputStream.flush();
    }
  }

  /**
   * 验证PNG文件是否为RGBA格式
   */
  public static void checkPngFormat(String pngPath) {
    try {
      java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.File(pngPath));
      int colorModel = image.getColorModel().getColorSpace().getType();
      boolean hasAlpha = image.getColorModel().hasAlpha();

      System.out.println("PNG文件信息：");
      System.out.println("颜色空间类型：" + colorModel);
      System.out.println("是否包含Alpha通道：" + hasAlpha);
      System.out.println("颜色模型：" + image.getColorModel().getClass().getSimpleName());
      System.out.println("像素格式：" + image.getType());

      if (hasAlpha) {
        System.out.println("✓ 该PNG文件包含透明度信息（RGBA格式）");
      } else {
        System.out.println("✗ 该PNG文件不包含透明度信息（RGB格式）");
      }

    } catch (Exception e) {
      System.err.println("检查PNG格式时出错：" + e.getMessage());
    }
  }

  public static void main(String[] args) {
    try {
      String svgPath = "F:\\code\\rust\\project-litongjava\\tauri-glm\\icons\\src.svg";
      String pngPath = "F:\\code\\rust\\project-litongjava\\tauri-glm\\icons\\src.png";

      // 转换SVG到PNG
      convertSvgToPng(svgPath, pngPath, 1024, 1024);
      System.out.println("转换成功！");

      // 检查生成的PNG格式
      checkPngFormat(pngPath);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}