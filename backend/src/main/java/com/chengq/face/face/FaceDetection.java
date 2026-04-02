package com.chengq.face.face;

import java.util.List;

/**
 * 单张人脸的检测结果：像素框、置信度、可选 5 点关键点与 PNG Base64 裁剪。
 */
public record FaceDetection(
    BBox bbox,
    double score,
    List<Point2> landmarks,
    String cropImageBase64
) {
  public record BBox(int x, int y, int width, int height) {}

  public record Point2(double x, double y) {}
}

