package com.chengq.face.face.djl;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class ImageDecoders {

  private ImageDecoders() {}

  public static ImageData decode(byte[] imageBytes) throws IOException {
    if (imageBytes == null || imageBytes.length == 0) {
      throw new IOException("Empty image bytes");
    }
    BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
    if (src == null) {
      // Fallback for formats ImageIO.read doesn't register by magic bytes alone
      src = readWithReaders(imageBytes);
    }
    if (src == null) {
      throw new IOException("Unsupported or corrupt image");
    }
    BufferedImage rgb =
        new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
    Graphics2D g = rgb.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(src, 0, 0, null);
    } finally {
      g.dispose();
    }
    int w = rgb.getWidth();
    int h = rgb.getHeight();
    byte[] bgr = ((java.awt.image.DataBufferByte) rgb.getRaster().getDataBuffer()).getData();
    byte[] out = new byte[w * h * 3];
    for (int i = 0; i < w * h; i++) {
      int j = i * 3;
      out[j] = bgr[j + 2];
      out[j + 1] = bgr[j + 1];
      out[j + 2] = bgr[j];
    }
    return new ImageData(w, h, out);
  }

  private static BufferedImage readWithReaders(byte[] imageBytes) throws IOException {
    try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
      if (iis == null) {
        return null;
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
      if (!readers.hasNext()) {
        return null;
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(iis);
        return reader.read(0);
      } finally {
        reader.dispose();
      }
    }
  }
}
