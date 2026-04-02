package com.chengq.face.face;

/**
 * 人脸算法门面：检测、1:1 比对、阈值标定。
 * <p>具体实现（ONNX 推理、预处理）由 {@code com.chengq.face.face.djl} 等包提供。
 */
public interface FaceSdk {

  /**
   * 解码图像并运行检测，坐标与尺寸相对于原图。
   */
  DetectResult detect(byte[] imageBytes, DetectOptions options);

  /**
   * 各取最大脸 embedding，计算映射后的相似度并与阈值比较。
   *
   * @throws IllegalArgumentException 若任一侧未检出人脸（常见消息 {@code NO_FACE}）
   */
  VerifyResult verify(byte[] image1Bytes, byte[] image2Bytes, double threshold);

  /**
   * 根据同人/异人两对样本估计建议 {@code verify} 阈值。
   *
   * @throws IllegalArgumentException 若任一样本未检出人脸
   */
  VerifyCalibrationResult calibrate(
      byte[] samePersonImage1,
      byte[] samePersonImage2,
      byte[] differentPersonImage1,
      byte[] differentPersonImage2
  );
}

