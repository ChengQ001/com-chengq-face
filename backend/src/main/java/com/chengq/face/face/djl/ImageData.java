package com.chengq.face.face.djl;

/** RGB888, top-left origin, row-major interleaved (width * height * 3). */
public record ImageData(int width, int height, byte[] rgb) {

  public ImageData {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Invalid image size");
    }
    if (rgb.length != width * height * 3) {
      throw new IllegalArgumentException("RGB buffer size mismatch");
    }
  }

  /** Bilinear resize (RGB interleaved). */
  public static ImageData resizeBilinear(ImageData src, int newW, int newH) {
    if (newW <= 0 || newH <= 0) {
      throw new IllegalArgumentException("Invalid target size");
    }
    if (src.width() == newW && src.height() == newH) {
      return src;
    }
    byte[] out = new byte[newW * newH * 3];
    double sx = (double) src.width() / newW;
    double sy = (double) src.height() / newH;
    for (int y = 0; y < newH; y++) {
      for (int x = 0; x < newW; x++) {
        double ox = (x + 0.5) * sx - 0.5;
        double oy = (y + 0.5) * sy - 0.5;
        int di = (y * newW + x) * 3;
        out[di] = (byte) Math.round(sampleBilinear(src.rgb(), src.width(), src.height(), ox, oy, 0));
        out[di + 1] = (byte) Math.round(sampleBilinear(src.rgb(), src.width(), src.height(), ox, oy, 1));
        out[di + 2] = (byte) Math.round(sampleBilinear(src.rgb(), src.width(), src.height(), ox, oy, 2));
      }
    }
    return new ImageData(newW, newH, out);
  }

  public static ImageData paddedToDivisor(ImageData src, int divisor, int padValue) {
    int padW = ((src.width() - 1) / divisor + 1) * divisor;
    int padH = ((src.height() - 1) / divisor + 1) * divisor;
    if (padW == src.width() && padH == src.height()) {
      return src;
    }
    byte[] out = new byte[padW * padH * 3];
    for (int y = 0; y < padH; y++) {
      for (int x = 0; x < padW; x++) {
        int di = (y * padW + x) * 3;
        if (x < src.width() && y < src.height()) {
          int si = (y * src.width() + x) * 3;
          out[di] = src.rgb()[si];
          out[di + 1] = src.rgb()[si + 1];
          out[di + 2] = src.rgb()[si + 2];
        } else {
          out[di] = out[di + 1] = out[di + 2] = (byte) padValue;
        }
      }
    }
    return new ImageData(padW, padH, out);
  }

  /** BGR planar float NCHW [1,3,H,W], values 0–255 (matches OpenCV dnn::blobFromImage, no mean). */
  public float[] toBgrNchwBlob01() {
    int h = height;
    int w = width;
    float[] blob = new float[1 * 3 * h * w];
    int plane = h * w;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int i = (y * w + x) * 3;
        float r = rgb[i] & 0xff;
        float g = rgb[i + 1] & 0xff;
        float b = rgb[i + 2] & 0xff;
        int idx = y * w + x;
        blob[idx] = (float) b;
        blob[plane + idx] = (float) g;
        blob[2 * plane + idx] = (float) r;
      }
    }
    return blob;
  }

  public int rgbAt(int x, int y) {
    int i = (y * width + x) * 3;
    return (rgb[i] & 0xff) << 16 | (rgb[i + 1] & 0xff) << 8 | (rgb[i + 2] & 0xff);
  }

  public static void setRgb(byte[] buf, int w, int x, int y, int rgb888) {
    int i = (y * w + x) * 3;
    buf[i] = (byte) (rgb888 >> 16);
    buf[i + 1] = (byte) (rgb888 >> 8);
    buf[i + 2] = (byte) rgb888;
  }

  public static float sampleBilinear(byte[] rgb, int w, int h, double x, double y, int channel) {
    if (x < 0) {
      x = 0;
    }
    if (y < 0) {
      y = 0;
    }
    if (x > w - 1) {
      x = w - 1;
    }
    if (y > h - 1) {
      y = h - 1;
    }
    int x0 = (int) Math.floor(x);
    int y0 = (int) Math.floor(y);
    int x1 = Math.min(x0 + 1, w - 1);
    int y1 = Math.min(y0 + 1, h - 1);
    double dx = x - x0;
    double dy = y - y0;
    double i00 = rgb[(y0 * w + x0) * 3 + channel] & 0xff;
    double i01 = rgb[(y0 * w + x1) * 3 + channel] & 0xff;
    double i10 = rgb[(y1 * w + x0) * 3 + channel] & 0xff;
    double i11 = rgb[(y1 * w + x1) * 3 + channel] & 0xff;
    double v0 = i00 * (1 - dx) + i01 * dx;
    double v1 = i10 * (1 - dx) + i11 * dx;
    return (float) (v0 * (1 - dy) + v1 * dy);
  }
}
