package com.chengq.face.api.dto;

import java.util.List;

/** {@code POST /api/face/detect} 的 JSON 响应。 */
public record DetectResponse(
    int imageWidth,
    int imageHeight,
    List<Face> faces
) {
  public record Face(
      BBox bbox,
      double score,
      List<Point2> landmarks,
      String cropImageBase64
  ) {}

  public record BBox(int x, int y, int width, int height) {}

  public record Point2(double x, double y) {}
}

