package com.chengq.face.face.djl;

import ai.djl.ndarray.NDManager;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import com.chengq.face.face.DetectOptions;
import com.chengq.face.face.DetectResult;
import com.chengq.face.face.FaceDetection;
import com.chengq.face.face.FaceSdk;
import com.chengq.face.face.VerifyCalibrationResult;
import com.chengq.face.face.VerifyResult;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 基于 ONNX Runtime 的 {@link FaceSdk} 实现：YuNet 检测、SFace 特征、可选相似变换对齐到 112×112。
 * <p>模型路径默认为 classpath 下 {@code models/*.onnx}，可通过 {@code face.model.detect} / {@code face.model.recognize} 覆盖。
 */
@Service
public class DjlFaceSdk implements FaceSdk {

  private static final String DET_DEFAULT = "models/face_detection_yunet_2023mar.onnx";
  private static final String REC_DEFAULT = "models/face_recognition_sface_2021dec.onnx";

  static {
    try (NDManager ignored = NDManager.newBaseManager()) {
      // 触发 DJL 与 onnxruntime-engine 的类加载顺序，避免部分环境下首次张量 API 初始化竞态。
    } catch (Throwable ignored) {
      // 无默认 Engine 时仍可仅靠 ORT 跑推理，此处忽略。
    }
  }

  private final OrtEnvironment ortEnv;
  private final YuNetOnnxDetector detector;
  private final SFaceOnnxEmbedding recognizer;

  public DjlFaceSdk(
      @Value("${face.model.detect:" + DET_DEFAULT + "}") String detectClasspath,
      @Value("${face.model.recognize:" + REC_DEFAULT + "}") String recClasspath)
      throws IOException, OrtException {
    this.ortEnv = OrtEnvironment.getEnvironment();
    this.detector = YuNetOnnxDetector.load(ortEnv, ClasspathModelFile.materialize(detectClasspath).toString());
    this.recognizer = SFaceOnnxEmbedding.load(ortEnv, ClasspathModelFile.materialize(recClasspath).toString());
  }

  /** 释放 ORT Session 等资源。 */
  @PreDestroy
  public void shutdown() {
    try {
      detector.close();
      recognizer.close();
    } catch (OrtException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * 将原图右侧/下侧 pad 到 32 倍数后送入 YuNet，框与关键点再映射回原图坐标系。
   */
  @Override
  public DetectResult detect(byte[] imageBytes, DetectOptions options) {
    try {
      ImageData orig = ImageDecoders.decode(imageBytes);
      ImageData padded = ImageData.paddedToDivisor(orig, 32, 0);
      var params =
          new YuNetOnnxDetector.YuNetDetectParams(
              (float) options.scoreThreshold(),
              (float) options.nmsThreshold(),
              options.topK());
      List<float[]> raw = detector.detect(padded, params);
      List<FaceDetection> faces = new ArrayList<>();
      for (float[] row : raw) {
        faces.add(mapFace(orig, padded, row, options));
      }
      faces.sort(Comparator.comparingDouble(FaceDetection::score).reversed());
      return new DetectResult(orig.width(), orig.height(), faces);
    } catch (OrtException e) {
      throw new IllegalStateException("Face detection inference failed", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 将检测器输出的一行（pad 坐标）裁剪到原图范围并可选生成 landmarks / crop。 */
  private static FaceDetection mapFace(ImageData orig, ImageData padded, float[] row, DetectOptions opt) {
    int ow = orig.width();
    int oh = orig.height();
    int x = clamp((int) row[0], 0, ow - 1);
    int y = clamp((int) row[1], 0, oh - 1);
    int w = clamp((int) row[2], 1, ow - x);
    int h = clamp((int) row[3], 1, oh - y);
    double score = row[14];

    List<FaceDetection.Point2> landmarks = null;
    if (opt.returnLandmarks()) {
      landmarks = new ArrayList<>(5);
      for (int n = 0; n < 5; n++) {
        double lx = row[4 + 2 * n];
        double ly = row[4 + 2 * n + 1];
        if (lx > ow - 1) {
          lx = ow - 1;
        }
        if (ly > oh - 1) {
          ly = oh - 1;
        }
        if (lx < 0) {
          lx = 0;
        }
        if (ly < 0) {
          ly = 0;
        }
        landmarks.add(new FaceDetection.Point2(lx, ly));
      }
    }

    String cropB64 = null;
    if (opt.returnCrops() && opt.cropMaxSize() >= 32) {
      cropB64 = encodeCropBase64(orig, x, y, w, h, opt.cropMaxSize());
    }

    return new FaceDetection(new FaceDetection.BBox(x, y, w, h), score, landmarks, cropB64);
  }

  private static String encodeCropBase64(ImageData orig, int x, int y, int w, int bh, int cropMaxSize) {
    try {
      BufferedImage sub = new BufferedImage(w, bh, BufferedImage.TYPE_3BYTE_BGR);
      byte[] bgr = ((java.awt.image.DataBufferByte) sub.getRaster().getDataBuffer()).getData();
      for (int yy = 0; yy < bh; yy++) {
        for (int xx = 0; xx < w; xx++) {
          int ox = x + xx;
          int oy = y + yy;
          int rgb = orig.rgbAt(ox, oy);
          int bi = (yy * w + xx) * 3;
          bgr[bi + 2] = (byte) (rgb >> 16);
          bgr[bi + 1] = (byte) (rgb >> 8);
          bgr[bi] = (byte) rgb;
        }
      }
      int tw = w;
      int th = bh;
      if (cropMaxSize > 0 && (tw > cropMaxSize || th > cropMaxSize)) {
        double sc = Math.min((double) cropMaxSize / tw, (double) cropMaxSize / th);
        int nw = Math.max(1, (int) Math.round(tw * sc));
        int nh = Math.max(1, (int) Math.round(th * sc));
        BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = scaled.createGraphics();
        try {
          g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          g.drawImage(sub, 0, 0, nw, nh, null);
        } finally {
          g.dispose();
        }
        sub = scaled;
      }
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ImageIO.write(sub, "png", bos);
      return Base64.getEncoder().encodeToString(bos.toByteArray());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * 各图取分数最高人脸，经五点仿射对齐后提取 128 维特征；相似度为余弦映射到 [0,1]。
   */
  @Override
  public VerifyResult verify(byte[] image1Bytes, byte[] image2Bytes, double threshold) {
    try {
      float[] e1 = embedLargestFace(image1Bytes);
      float[] e2 = embedLargestFace(image2Bytes);
      float[] n1 = AlignAndEmbed.normalizedCopy(e1);
      float[] n2 = AlignAndEmbed.normalizedCopy(e2);
      double raw = AlignAndEmbed.cosineNormalized(n1, n2);
      double similarity = (raw + 1.0) / 2.0;
      boolean same = similarity >= threshold;
      return new VerifyResult(raw, similarity, threshold, same);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (OrtException e) {
      throw new IllegalStateException("Face recognition inference failed", e);
    }
  }

  @Override
  public VerifyCalibrationResult calibrate(
      byte[] samePersonImage1,
      byte[] samePersonImage2,
      byte[] differentPersonImage1,
      byte[] differentPersonImage2) {
    try {
      float[] s1 = embedLargestFace(samePersonImage1);
      float[] s2 = embedLargestFace(samePersonImage2);
      float[] d1 = embedLargestFace(differentPersonImage1);
      float[] d2 = embedLargestFace(differentPersonImage2);

      double sameSim =
          (AlignAndEmbed.cosineNormalized(
                  AlignAndEmbed.normalizedCopy(s1), AlignAndEmbed.normalizedCopy(s2))
              + 1.0F)
              / 2.0;
      double diffSim =
          (AlignAndEmbed.cosineNormalized(
                  AlignAndEmbed.normalizedCopy(d1), AlignAndEmbed.normalizedCopy(d2))
              + 1.0F)
              / 2.0;

      double suggested = (sameSim + diffSim) / 2.0;
      suggested = Math.min(0.995, Math.max(suggested, diffSim + 0.02));
      String note =
          "Cosine similarity mapped to [0,1] as (rawCosine+1)/2; tighten threshold if "
              + "same-pair score is not comfortably above different-pair score.";
      return new VerifyCalibrationResult(sameSim, diffSim, suggested, note);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (OrtException e) {
      throw new IllegalStateException("Face recognition inference failed", e);
    }
  }

  /**
   * 检测最高分脸 → 标准 5 点对齐 → SFace 输入 blob → 嵌入向量。
   *
   * @throws IllegalArgumentException 未检出人脸时 {@code NO_FACE}
   */
  private float[] embedLargestFace(byte[] imageBytes) throws IOException, OrtException {
    ImageData orig = ImageDecoders.decode(imageBytes);
    ImageData padded = ImageData.paddedToDivisor(orig, 32, 0);
    var params = new YuNetOnnxDetector.YuNetDetectParams(0.6f, 0.3f, 100);
    List<float[]> dets = detector.detect(padded, params);
    if (dets.isEmpty()) {
      throw new IllegalArgumentException("NO_FACE");
    }
    float[] best = dets.stream().max(Comparator.comparingDouble(a -> a[14])).orElseThrow();
    float[][] lm = OpenCvSimilarityTransform.landmarksFromDetectorRow(best);
    double[] m = OpenCvSimilarityTransform.getMatrix(lm);
    byte[] aligned = AlignAndEmbed.warpAffineRgb112(orig, m);
    float[] blob = AlignAndEmbed.alignedRgbToSfaceBlob(aligned);
    return recognizer.embed(blob);
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }
}
