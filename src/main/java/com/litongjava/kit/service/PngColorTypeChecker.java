package com.litongjava.kit.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class PngColorTypeChecker {

  public static void main(String[] args) throws Exception {
    String pngPath = "F:\\code\\rust\\project-litongjava\\tauri-myget\\icons\\src.png";

    System.out.println("=== PNG文件分析结果 ===");
    System.out.println("文件路径: " + pngPath);
    System.out.println("是否为RGBA格式? " + isRgbaFormat(pngPath));

    // 额外的检测方法作为对比
    checkPngDetailsWithBufferedImage(pngPath);
  }

  /**
   * 通过PNG元数据检测是否为RGBA格式 (Java 1.8兼容版本)
   * 
   * @param pngPath PNG文件路径
   * @return true如果是RGBA格式(colorType=6)
   */
  public static boolean isRgbaFormat(String pngPath) throws Exception {
    File file = new File(pngPath);
    if (!file.exists()) {
      throw new IllegalArgumentException("文件不存在: " + pngPath);
    }

    ImageInputStream stream = null;
    try {
      stream = ImageIO.createImageInputStream(file);
      if (stream == null) {
        throw new RuntimeException("无法创建ImageInputStream");
      }

      Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) {
        throw new RuntimeException("没有找到PNG的ImageReader");
      }

      ImageReader reader = readers.next();
      try {
        reader.setInput(stream);
        IIOMetadata metadata = reader.getImageMetadata(0);

        if (metadata == null) {
          System.out.println("警告: 无法获取PNG元数据");
          return false;
        }

        // 获取PNG原生格式的元数据树
        String[] formatNames = metadata.getMetadataFormatNames();
        System.out.println("可用的元数据格式: " + formatNamesToString(formatNames));

        // 尝试PNG 1.0格式
        for (int i = 0; i < formatNames.length; i++) {
          if ("javax_imageio_png_1.0".equals(formatNames[i])) {
            try {
              Node root = metadata.getAsTree("javax_imageio_png_1.0");
              return checkColorTypeFromMetadata(root);
            } catch (Exception e) {
              System.out.println("PNG 1.0格式解析失败: " + e.getMessage());
            }
          }
        }

        // 尝试标准格式
        for (int i = 0; i < formatNames.length; i++) {
          if ("javax_imageio_1.0".equals(formatNames[i])) {
            try {
              Node root = metadata.getAsTree("javax_imageio_1.0");
              return checkColorTypeFromStandardMetadata(root);
            } catch (Exception e) {
              System.out.println("标准格式解析失败: " + e.getMessage());
            }
          }
        }

        System.out.println("警告: 不支持的元数据格式");
        return false;

      } finally {
        reader.dispose();
      }
    } finally {
      if (stream != null) {
        stream.close();
      }
    }
  }

  /**
   * Java 1.8兼容的数组转字符串方法
   */
  private static String formatNamesToString(String[] array) {
    if (array == null || array.length == 0) {
      return "[]";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < array.length; i++) {
      if (i > 0)
        sb.append(", ");
      sb.append(array[i]);
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * 从PNG特定的元数据树中检查颜色类型
   */
  private static boolean checkColorTypeFromMetadata(Node root) {
    Node ihdr = findNode(root, "IHDR");
    if (ihdr != null) {
      NamedNodeMap attrs = ihdr.getAttributes();
      Node colorTypeNode = attrs.getNamedItem("colorType");
      if (colorTypeNode != null) {
        String colorType = colorTypeNode.getNodeValue();
        System.out.println("PNG颜色类型: " + colorType);
        System.out.println("颜色类型含义: " + getColorTypeDescription(colorType));

        // ColorType 6 = Truecolor with alpha (RGBA)
        // ColorType 4 = Grayscale with alpha
        return "6".equals(colorType) || "4".equals(colorType);
      }
    }
    return false;
  }

  /**
   * 从标准元数据树中检查透明度信息
   */
  private static boolean checkColorTypeFromStandardMetadata(Node root) {
    // 在标准格式中查找透明度信息
    Node transparency = findNode(root, "Transparency");
    if (transparency != null) {
      Node alpha = findNode(transparency, "Alpha");
      return alpha != null;
    }
    return false;
  }

  /**
   * 使用BufferedImage进行额外检测（作为备用方法）
   */
  public static void checkPngDetailsWithBufferedImage(String pngPath) {
    try {
      BufferedImage image = ImageIO.read(new File(pngPath));
      if (image != null) {
        System.out.println("\n=== BufferedImage分析结果 ===");
        System.out.println("图像类型: " + getBufferedImageTypeDescription(image.getType()));
        System.out.println("是否有Alpha通道: " + image.getColorModel().hasAlpha());
        System.out.println("颜色模型: " + image.getColorModel().getClass().getSimpleName());
        System.out.println("像素大小: " + image.getColorModel().getPixelSize() + " bits");
        System.out.println("颜色组件数: " + image.getColorModel().getNumComponents());
        System.out.println("图像尺寸: " + image.getWidth() + "x" + image.getHeight());
      }
    } catch (Exception e) {
      System.err.println("BufferedImage分析失败: " + e.getMessage());
    }
  }

  /**
   * 递归查找指定名称的节点
   */
  private static Node findNode(Node node, String nodeName) {
    if (node == null)
      return null;

    if (nodeName.equals(node.getNodeName())) {
      return node;
    }

    for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
      Node found = findNode(child, nodeName);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /**
   * 获取PNG颜色类型的描述
   */
  private static String getColorTypeDescription(String colorType) {
    if ("0".equals(colorType))
      return "Grayscale";
    if ("2".equals(colorType))
      return "Truecolor (RGB)";
    if ("3".equals(colorType))
      return "Indexed-color";
    if ("4".equals(colorType))
      return "Grayscale with alpha";
    if ("6".equals(colorType))
      return "Truecolor with alpha (RGBA)";
    return "Unknown (" + colorType + ")";
  }

  /**
   * 获取BufferedImage类型的描述
   */
  private static String getBufferedImageTypeDescription(int type) {
    switch (type) {
    case BufferedImage.TYPE_INT_ARGB:
      return "TYPE_INT_ARGB (RGBA)";
    case BufferedImage.TYPE_INT_ARGB_PRE:
      return "TYPE_INT_ARGB_PRE (RGBA预乘)";
    case BufferedImage.TYPE_INT_RGB:
      return "TYPE_INT_RGB";
    case BufferedImage.TYPE_INT_BGR:
      return "TYPE_INT_BGR";
    case BufferedImage.TYPE_3BYTE_BGR:
      return "TYPE_3BYTE_BGR";
    case BufferedImage.TYPE_4BYTE_ABGR:
      return "TYPE_4BYTE_ABGR (RGBA)";
    case BufferedImage.TYPE_4BYTE_ABGR_PRE:
      return "TYPE_4BYTE_ABGR_PRE (RGBA预乘)";
    case BufferedImage.TYPE_BYTE_GRAY:
      return "TYPE_BYTE_GRAY";
    case BufferedImage.TYPE_USHORT_GRAY:
      return "TYPE_USHORT_GRAY";
    case BufferedImage.TYPE_BYTE_BINARY:
      return "TYPE_BYTE_BINARY";
    case BufferedImage.TYPE_BYTE_INDEXED:
      return "TYPE_BYTE_INDEXED";
    case BufferedImage.TYPE_USHORT_565_RGB:
      return "TYPE_USHORT_565_RGB";
    case BufferedImage.TYPE_USHORT_555_RGB:
      return "TYPE_USHORT_555_RGB";
    default:
      return "Custom type (" + type + ")";
    }
  }
}