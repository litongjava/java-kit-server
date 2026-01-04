package com.litongjava.kit.utils;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import javax.imageio.ImageIO;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PptUtils {
  public static void addImage(File[] imageFiles, String outPath) {
    if (imageFiles == null || imageFiles.length == 0) {
      return;
    }

    // 2. 按名字排序
    Arrays.sort(imageFiles, Comparator.comparing(File::getName));

    // 3. 创建 PPTX
    XMLSlideShow ppt = null;
    try {
      ppt = new XMLSlideShow();

      // PPT 页面尺寸（默认是宽屏 10x5.63 英寸之类的）
      Dimension pageSize = ppt.getPageSize();
      int slideWidth = pageSize.width;
      int slideHeight = pageSize.height;

      for (File imgFile : imageFiles) {
        addImage(ppt, imgFile, slideWidth, slideHeight);
      }

      // 7. 保存 PPTX
      try (FileOutputStream out = new FileOutputStream(outPath)) {
        ppt.write(out);
      } catch (FileNotFoundException e) {
        log.error(e.getMessage(), e);
      } catch (IOException e) {
        log.error(e.getMessage(), e);
      }
    } finally {
      try {
        ppt.close();
      } catch (IOException e) {
        log.error(e.getMessage(), e);
      }
    }
  }

  private static void addImage(XMLSlideShow ppt, File imgFile, int slideWidth, int slideHeight) {
    // 4. 新建一页
    XSLFSlide slide = ppt.createSlide();
    // 5. 读入图片并添加为 PictureData
    try (FileInputStream fis = new FileInputStream(imgFile)) {
      byte[] pictureData = fis.readAllBytes();
      XSLFPictureData.PictureType type = getPictureType(imgFile.getName());
      XSLFPictureData xslfPicData = ppt.addPicture(pictureData, type);
      // 读取原始图片尺寸
      BufferedImage bufferedImage;
      try (FileInputStream imageFis = new FileInputStream(imgFile)) {
        bufferedImage = ImageIO.read(imageFis);
      }

      int imgW = bufferedImage.getWidth();
      int imgH = bufferedImage.getHeight();

      // 计算缩放比例（仅当图片比幻灯片大时缩小）
      double scale = Math.min((double) slideWidth / imgW, (double) slideHeight / imgH);

      // 如果scale > 1，说明图片更小，则保持原尺寸，设为1
      if (scale > 1) {
        scale = 1;
      }

      // 缩放后的尺寸
      int newW = (int) (imgW * scale);
      int newH = (int) (imgH * scale);

      // 计算居中位置
      int posX = (slideWidth - newW) / 2;
      int posY = (slideHeight - newH) / 2;

      // 创建图片并放置
      XSLFPictureShape picShape = slide.createPicture(xslfPicData);
      picShape.setAnchor(new Rectangle(posX, posY, newW, newH));
    } catch (FileNotFoundException e) {
      log.error(e.getMessage(), e);
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
  }

  private static XSLFPictureData.PictureType getPictureType(String name) {
    String lower = name.toLowerCase();
    if (lower.endsWith(".png"))
      return XSLFPictureData.PictureType.PNG;
    if (lower.endsWith(".gif"))
      return XSLFPictureData.PictureType.GIF;
    if (lower.endsWith(".bmp"))
      return XSLFPictureData.PictureType.BMP;
    // 默认当作 JPEG
    return XSLFPictureData.PictureType.JPEG;
  }
}
