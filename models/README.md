# Face ONNX models (classpath)

**文档位置**：本说明在仓库根目录 `models/README.md`。  
**权重文件位置**：请将下表中的 `.onnx` 放到 **`backend/src/main/resources/models/`**（与历史约定一致），构建后位于 **`classpath:models/`**，供 `DjlFaceSdk` 加载。

**中文说明**：后端使用 OpenCV Zoo 公开的 YuNet（检测）与 SFace（识别）ONNX；须与下表**文件名或 `application.yml` 配置路径**一致。检测模型为 **固定 640×640** 输入的 12 路输出结构；识别模型需与本文「Recognizer 预处理」一致，否则会大幅掉精度。

Place ONNX binaries under **`backend/src/main/resources/models/`** (packaged as `classpath:models/`):

| File | Purpose | Suggested source |
|------|---------|------------------|
| `face_detection_yunet_2023mar.onnx` | YuNet 12-output detector (OpenCV `FaceDetectorYN` ONNX) | [opencv_zoo `face_detection_yunet`](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet) |
| `face_recognition_sface_2021dec.onnx` | SFace 128-D embedding (OpenCV `FaceRecognizerSF`) | [opencv_zoo `face_recognition_sface`](https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface) |

## YuNet（检测）约定

- **输入**：单张图缩放到 **640×640**；张量形状 **1×3×640×640**，通道顺序 **BGR**，像素 **÷255** 到 **[0,1]**（与实现中 `toBgrNchwBlob01()` 一致）。
- **输出**：12 个张量，名称需包含 OpenCV Zoo 约定：`cls_8` … `kps_32`（见源码 `YuNetOnnxDetector.OUTPUT_NAMES`）。内部将 flat buffer 按 **OpenCV postProcess** 语义解码，再在 **pad 后画布** 上得到框与点，最后映射回原图。

## Recognizer（SFace）预处理

必须与下列约定一致（对齐 OpenCV `FaceRecognizerSF::feature` / `blobFromImage`，`swapRB=true`）：

- 人脸经 **5 点相似变换** 对齐到 **112×112**，**RGB** planar **float**，数值范围 **0–255**（非 0–1），再按 NCHW 喂入 ONNX。

若更换为其他识别 ONNX，需同步修改 `AlignAndEmbed` 与 `SFaceOnnxEmbedding` 中的预处理，否则不要仅改路径。

## 下载与 Git LFS

- GitHub 仓库中 `.onnx` 可能由 **Git LFS** 管理；直接在网页点 raw 可能得到指针文件。建议：使用 **[Release 附件](https://github.com/opencv/opencv_zoo/releases)**、完整 `git lfs clone`，或可信镜像（如部分 Hugging Face 上的 zoo 导出，仍需核对哈希与许可证）。

## 覆盖路径

`application.yml` 示例：

```yaml
face:
  model:
    detect: models/your_detector.onnx
    recognize: models/your_recognizer.onnx
```

## Licenses

- **Framework**: Apache ONNX Runtime, Apache DJL API; OpenCV Zoo **model READMEs** often use Apache 2.0 for published artifacts—**confirm per file**.
- **Weights / 权重**：商业使用条件以各模型及 Zoo 声明为准；生产环境务必做合规审查。
