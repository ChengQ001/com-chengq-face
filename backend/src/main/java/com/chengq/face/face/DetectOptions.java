package com.chengq.face.face;

/**
 * 检测流水线参数：是否附带关键点/裁剪、NMS 与置信度阈值等。
 * <p>{@code scoreThreshold} 为 0 时表示不在检测阶段按分过滤（由调用方自行过滤），{@link com.chengq.face.api.FaceController#check} 会用到。
 */
public record DetectOptions(
    boolean returnLandmarks,
    boolean returnCrops,
    int cropMaxSize,
    double scoreThreshold,
    double nmsThreshold,
    int topK
) {
  public static DetectOptions defaults() {
    return new DetectOptions(true, true, 256, 0.9, 0.3, 5000);
  }
}

