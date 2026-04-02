package com.chengq.face.face.djl;

import java.util.Arrays;

/** OpenCV {@code alignCrop} + SFace {@code blobFromImage(..., swapRB=true)} on RGB buffers. */
public final class AlignAndEmbed {

  private AlignAndEmbed() {}

  /** Bilinear warp from {@code src} using OpenCV warpAffine convention (dst(x,y)←src). */
  public static byte[] warpAffineRgb112(ImageData src, double[] m2x3) {
    int dw = 112;
    int dh = 112;
    byte[] dst = new byte[dw * dh * 3];
    double m00 = m2x3[0];
    double m01 = m2x3[1];
    double m02 = m2x3[2];
    double m10 = m2x3[3];
    double m11 = m2x3[4];
    double m12 = m2x3[5];
    int sw = src.width();
    int sh = src.height();
    byte[] s = src.rgb();
    for (int y = 0; y < dh; y++) {
      for (int x = 0; x < dw; x++) {
        double sx = m00 * x + m01 * y + m02;
        double sy = m10 * x + m11 * y + m12;
        int di = (y * dw + x) * 3;
        dst[di] = (byte) Math.round(ImageData.sampleBilinear(s, sw, sh, sx, sy, 0));
        dst[di + 1] = (byte) Math.round(ImageData.sampleBilinear(s, sw, sh, sx, sy, 1));
        dst[di + 2] = (byte) Math.round(ImageData.sampleBilinear(s, sw, sh, sx, sy, 2));
      }
    }
    return dst;
  }

  /** Planar R,G,B float [1,3,112,112], 0–255 (OpenCV SFace feature preprocess). */
  public static float[] alignedRgbToSfaceBlob(byte[] rgb112) {
    int w = 112;
    int h = 112;
    float[] blob = new float[3 * w * h];
    int plane = w * h;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int i = (y * w + x) * 3;
        float rv = rgb112[i] & 0xff;
        float gv = rgb112[i + 1] & 0xff;
        float bv = rgb112[i + 2] & 0xff;
        int idx = y * w + x;
        blob[idx] = rv;
        blob[plane + idx] = gv;
        blob[2 * plane + idx] = bv;
      }
    }
    return blob;
  }

  public static void l2Normalize(float[] v) {
    double sum = 0;
    for (float f : v) {
      sum += (double) f * f;
    }
    double n = Math.sqrt(sum);
    if (n < 1e-12) {
      return;
    }
    for (int i = 0; i < v.length; i++) {
      v[i] = (float) (v[i] / n);
    }
  }

  public static double cosineNormalized(float[] a, float[] b) {
    double s = 0;
    for (int i = 0; i < a.length; i++) {
      s += a[i] * b[i];
    }
    return s;
  }

  /** Clone and L2-normalize a copy. */
  public static float[] normalizedCopy(float[] embedding) {
    float[] c = Arrays.copyOf(embedding, embedding.length);
    l2Normalize(c);
    return c;
  }
}
