package com.chengq.face.api;

import com.chengq.face.api.dto.DetectResponse;
import com.chengq.face.api.dto.FaceCheckResponse;
import com.chengq.face.face.DetectOptions;
import com.chengq.face.face.FaceSdk;
import com.chengq.face.face.VerifyCalibrationResult;
import com.chengq.face.face.VerifyResult;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人脸相关 HTTP 接口：检测、单图校验、双图比对、阈值标定。
 * <p>请求体均为 {@code multipart/form-data}；校验/比对失败时可能由 {@link ApiExceptionAdvice} 返回 400 与 {@code message}。
 */
@Validated
@RestController
@RequestMapping("/api/face")
public class FaceController {

  private final FaceSdk faceSdk;

  public FaceController(FaceSdk faceSdk) {
    this.faceSdk = faceSdk;
  }

  /**
   * 检测图中所有人脸，可选返回 5 点关键点与人脸裁剪（PNG Base64）。
   *
   * @param file            图片字节（常见 image/jpeg、image/png）
   * @param returnLandmarks 是否在响应中包含 landmarks
   * @param returnCrops     是否生成并返回 crop 的 Base64（需 {@code cropMaxSize &gt;= 32} 才生效）
   * @param cropMaxSize     裁剪最长边缩放上界（像素）
   * @param scoreThreshold  检测置信度下限
   * @param nmsThreshold   NMS IoU 阈值
   * @param topK           每尺度保留的候选上限（供后处理截断）
   */
  @PostMapping(value = "/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DetectResponse detect(
      @RequestParam("file") @NotNull MultipartFile file,
      @RequestParam(name = "returnLandmarks", defaultValue = "true") boolean returnLandmarks,
      @RequestParam(name = "returnCrops", defaultValue = "true") boolean returnCrops,
      @RequestParam(name = "cropMaxSize", defaultValue = "256") @Min(32) @Max(1024) int cropMaxSize,
      @RequestParam(name = "scoreThreshold", defaultValue = "0.9")
          @DecimalMin("0.0") @DecimalMax("1.0") double scoreThreshold,
      @RequestParam(name = "nmsThreshold", defaultValue = "0.3")
          @DecimalMin("0.0") @DecimalMax("1.0") double nmsThreshold,
      @RequestParam(name = "topK", defaultValue = "5000") @Min(1) @Max(20000) int topK
  ) throws IOException {
    byte[] bytes = file.getBytes();
    var opts = new DetectOptions(
        returnLandmarks,
        returnCrops,
        cropMaxSize,
        scoreThreshold,
        nmsThreshold,
        topK
    );

    var result = faceSdk.detect(bytes, opts);
    var faces = result.faces().stream().map(f -> new DetectResponse.Face(
        new DetectResponse.BBox(
            f.bbox().x(),
            f.bbox().y(),
            f.bbox().width(),
            f.bbox().height()
        ),
        f.score(),
        f.landmarks() == null ? null : f.landmarks().stream()
            .map(p -> new DetectResponse.Point2(p.x(), p.y()))
            .toList(),
        f.cropImageBase64()
    )).toList();

    return new DetectResponse(result.imageWidth(), result.imageHeight(), faces);
  }

  /**
   * 对两张图各自取置信度最高的人脸，提取特征后计算相似度，与阈值比较判定是否同一人。
   * <p>相似度定义为归一化余弦后的线性映射：{@code similarity = (rawCosine + 1) / 2}，范围约 [0, 1]。
   * <p>任一图中未检测到人脸时抛出 {@link IllegalArgumentException}（消息通常为 {@code NO_FACE}），由全局异常处理映射为 HTTP 400。
   *
   * @param file1     第一张图
   * @param file2     第二张图
   * @param threshold {@code similarity} 大于等于该值视为同人
   */
  @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public VerifyResult verify(
      @RequestParam("file1") @NotNull MultipartFile file1,
      @RequestParam("file2") @NotNull MultipartFile file2,
      @RequestParam(name = "threshold", defaultValue = "0.95")
          @DecimalMin("0.0") @DecimalMax("1.0") double threshold
  ) throws IOException {
    return faceSdk.verify(file1.getBytes(), file2.getBytes(), threshold);
  }

  /**
   * 用两对样本（同人一对、异人一对）各算一次 {@code similarity}，据此给出建议阈值，便于运维调参。
   * <p>四张图均需能检测到人脸，否则抛出 {@link IllegalArgumentException}（{@code NO_FACE}）。
   *
   * @param same1 同人样本图 1
   * @param same2 同人样本图 2
   * @param diff1 异人样本图 1
   * @param diff2 异人样本图 2
   */
  @PostMapping(value = "/verify/calibrate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public VerifyCalibrationResult calibrateVerify(
      @RequestParam("same1") @NotNull MultipartFile same1,
      @RequestParam("same2") @NotNull MultipartFile same2,
      @RequestParam("diff1") @NotNull MultipartFile diff1,
      @RequestParam("diff2") @NotNull MultipartFile diff2
  ) throws IOException {
    return faceSdk.calibrate(
        same1.getBytes(),
        same2.getBytes(),
        diff1.getBytes(),
        diff2.getBytes()
    );
  }

  /**
   * 单图快速校验：基于检测置信度判断是否「有脸 / 是否仅一张脸 / 最高分是否达标」。
   * <p>内部先用宽松的检测参数拉全候选，再在应用层按 {@code threshold} 过滤计数；reason 取值见 {@link FaceCheckResponse}。
   *
   * @param file               待校验图片
   * @param threshold          判定为「有人脸」的检测置信度下限
   * @param requireSingleFace  为 true 时，高于阈值的检测框数量必须为 1 才视为通过
   */
  @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public FaceCheckResponse check(
      @RequestParam("file") @NotNull MultipartFile file,
      @RequestParam(name = "threshold", defaultValue = "0.9")
          @DecimalMin("0.0") @DecimalMax("1.0") double threshold,
      @RequestParam(name = "requireSingleFace", defaultValue = "true") boolean requireSingleFace
  ) throws IOException {
    var opts = new DetectOptions(
        false,   // returnLandmarks
        false,   // returnCrops
        0,       // cropMaxSize
        0.0,     // scoreThreshold (collect all candidates; we count by threshold below)
        0.3,
        5000
    );
    var result = faceSdk.detect(file.getBytes(), opts);
    var above = result.faces().stream()
        .map(f -> f.score())
        .filter(s -> s >= threshold)
        .toList();
    int count = above.size();
    Double best = above.stream().max(Double::compareTo).orElse(null);

    String reason;
    boolean ok;
    if (count <= 0) {
      ok = false;
      reason = "NO_FACE";
    } else if (requireSingleFace && count != 1) {
      ok = false;
      reason = "MULTIPLE_FACES";
    } else if (best != null && best >= threshold) {
      ok = true;
      reason = "OK";
    } else {
      ok = false;
      reason = "LOW_SCORE";
    }

    return new FaceCheckResponse(ok, threshold, requireSingleFace, count, best, reason);
  }
}

