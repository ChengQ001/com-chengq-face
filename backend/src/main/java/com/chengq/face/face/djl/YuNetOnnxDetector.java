package com.chengq.face.face.djl;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * YuNet post-processing aligned with OpenCV {@code FaceDetectorYNImpl::postProcess} and 12-output
 * ONNX from the OpenCV zoo.
 */
public final class YuNetOnnxDetector implements AutoCloseable {

  /** OpenCV zoo YuNet 2023mar ONNX uses a fixed 640×640 input (see session input TensorInfo). */
  private static final int YUNET_INPUT = 640;

  private static final int[] STRIDES = {8, 16, 32};
  private static final String[] OUTPUT_NAMES = {
    "cls_8", "cls_16", "cls_32",
    "obj_8", "obj_16", "obj_32",
    "bbox_8", "bbox_16", "bbox_32",
    "kps_8", "kps_16", "kps_32"
  };

  private final OrtEnvironment env;
  private final OrtSession session;
  private final String inputName;

  public YuNetOnnxDetector(OrtEnvironment env, OrtSession session) throws OrtException {
    this.env = env;
    this.session = session;
    this.inputName = session.getInputNames().iterator().next();
  }

  public static YuNetOnnxDetector load(OrtEnvironment env, String modelPath)
      throws OrtException {
    OrtSession session =
        env.createSession(
            modelPath,
            new OrtSession.SessionOptions());
    return new YuNetOnnxDetector(env, session);
  }

  @Override
  public void close() throws OrtException {
    session.close();
  }

  /**
   * Each row: x1, y1, w, h, kps×5×2, score (15 floats), in padded image coordinates (same as
   * OpenCV).
   */
  public List<float[]> detect(ImageData paddedRgb, YuNetDetectParams p) throws OrtException {
    int padW = paddedRgb.width();
    int padH = paddedRgb.height();
    float scaleX = padW / (float) YUNET_INPUT;
    float scaleY = padH / (float) YUNET_INPUT;
    ImageData netIn = ImageData.resizeBilinear(paddedRgb, YUNET_INPUT, YUNET_INPUT);

    float[] blob = netIn.toBgrNchwBlob01();
    long[] shape = {1, 3, YUNET_INPUT, YUNET_INPUT};
    try (OnnxTensor input =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(blob), shape)) {

      try (OrtSession.Result result = session.run(Map.of(inputName, input))) {
        float[][] outs = new float[OUTPUT_NAMES.length][];
        for (int i = 0; i < OUTPUT_NAMES.length; i++) {
          OnnxTensor t = (OnnxTensor) result.get(OUTPUT_NAMES[i]).get();
          java.nio.FloatBuffer fb = t.getFloatBuffer();
          float[] flat = new float[fb.remaining()];
          fb.get(flat);
          outs[i] = flat;
        }
        List<float[]> faces =
            postProcess(outs, YUNET_INPUT, YUNET_INPUT, p.scoreThreshold(), p.nmsThreshold(), p.topK());
        scaleToPaddedSpace(faces, scaleX, scaleY);
        return faces;
      }
    }
  }

  private static void scaleToPaddedSpace(List<float[]> faces, float sx, float sy) {
    for (float[] row : faces) {
      row[0] *= sx;
      row[1] *= sy;
      row[2] *= sx;
      row[3] *= sy;
      for (int n = 0; n < 5; n++) {
        row[4 + 2 * n] *= sx;
        row[4 + 2 * n + 1] *= sy;
      }
    }
  }

  private static List<float[]> postProcess(
      float[][] outputBlobs, int padW, int padH, float scoreThreshold, float nmsThreshold, int topK) {

    List<float[]> faces = new ArrayList<>();

    for (int si = 0; si < STRIDES.length; si++) {
      int stride = STRIDES[si];
      int cols = padW / stride;
      int rows = padH / stride;

      float[] cls = outputBlobs[si];
      float[] obj = outputBlobs[si + STRIDES.length];
      float[] bbox = outputBlobs[si + 2 * STRIDES.length];
      float[] kps = outputBlobs[si + 3 * STRIDES.length];

      if (cls.length < rows * cols
          || obj.length < rows * cols
          || bbox.length < 4 * rows * cols
          || kps.length < 10 * rows * cols) {
        throw new IllegalStateException(
            "Unexpected YuNet tensor size for stride " + stride + "; check ONNX export version.");
      }

      int rc = rows * cols;
      for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
          int spatial = r * cols + c;

          float clsScore = Math.min(Math.max(cls[spatial], 0f), 1f);
          float objScore = Math.min(Math.max(obj[spatial], 0f), 1f);
          float score = (float) Math.sqrt(clsScore * objScore);

          if (score < scoreThreshold) {
            continue;
          }

          // Zoo ONNX layout [1, H*W, C] (same linear idx as OpenCV FaceDetectorYN postProcess)
          int bi = spatial * 4;
          float b0 = bbox[bi];
          float b1 = bbox[bi + 1];
          float b2 = bbox[bi + 2];
          float b3 = bbox[bi + 3];
          float cx = ((c + b0) * stride);
          float cy = ((r + b1) * stride);
          float w = (float) (Math.exp(b2) * stride);
          float h = (float) (Math.exp(b3) * stride);
          float x1 = cx - w / 2f;
          float y1 = cy - h / 2f;

          float[] row = new float[15];
          row[0] = x1;
          row[1] = y1;
          row[2] = w;
          row[3] = h;
          int ki = spatial * 10;
          for (int n = 0; n < 5; n++) {
            row[4 + 2 * n] = (kps[ki + 2 * n] + c) * stride;
            row[4 + 2 * n + 1] = (kps[ki + 2 * n + 1] + r) * stride;
          }
          row[14] = score;
          faces.add(row);
        }
      }
    }

    if (faces.size() <= 1) {
      return faces;
    }

    List<Rect2i> boxes =
        faces.stream()
            .map(
                f ->
                    new Rect2i(
                        (int) f[0],
                        (int) f[1],
                        (int) f[2],
                        (int) f[3]))
            .toList();
    List<Float> scores = faces.stream().map(f -> f[14]).toList();
    int[] keep = nmsIndices(boxes, scores, scoreThreshold, nmsThreshold, topK);
    List<float[]> out = new ArrayList<>(keep.length);
    for (int k : keep) {
      out.add(faces.get(k));
    }
    return out;
  }

  /** Like OpenCV {@code dnn::NMSBoxes} with eta=1 and topK. */
  private static int[] nmsIndices(
      List<Rect2i> boxes, List<Float> scores, float scoreThresh, float nmsThresh, int topK) {
    List<Integer> order =
        IntStream.range(0, boxes.size())
            .filter(i -> scores.get(i) >= scoreThresh)
            .boxed()
            .sorted(Comparator.comparing((Integer i) -> scores.get(i)).reversed())
            .toList();

    List<Integer> keep = new ArrayList<>();
    for (int idx : order) {
      if (keep.size() >= topK) {
        break;
      }
      boolean ok = true;
      Rect2i b = boxes.get(idx);
      for (int j : keep) {
        Rect2i k = boxes.get(j);
        if (iou(b, k) > nmsThresh) {
          ok = false;
          break;
        }
      }
      if (ok) {
        keep.add(idx);
      }
    }
    return keep.stream().mapToInt(i -> i).toArray();
  }

  private static double iou(Rect2i a, Rect2i b) {
    int x1 = Math.max(a.x, b.x);
    int y1 = Math.max(a.y, b.y);
    int x2 = Math.min(a.x + a.w, b.x + b.w);
    int y2 = Math.min(a.y + a.h, b.y + b.h);
    int iw = Math.max(0, x2 - x1);
    int ih = Math.max(0, y2 - y1);
    double inter = iw * (double) ih;
    double u = a.w * (double) a.h + b.w * (double) b.h - inter;
    return u <= 0 ? 0 : inter / u;
  }

  public record YuNetDetectParams(float scoreThreshold, float nmsThreshold, int topK) {}

  private record Rect2i(int x, int y, int w, int h) {}
}
