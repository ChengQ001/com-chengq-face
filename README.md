# com-chengq-face

一个**免费开源**的人脸检测与 1:1 比对 Demo：

- **后端**：Spring Boot 3 + Java 17；Apache DJL API + **ONNX Runtime**；**YuNet** 检测、**SFace** 特征（无 OpenCV native 依赖）。
- **前端**：Vue3 + Vite（上传图片、绘制 bbox/关键点、展示裁剪缩略图；可选调用比对接口）。

## 架构要点

- 检测：原图按右侧/下侧 **pad 到 32 的倍数** 后双线性缩放到 **640×640**，输入为 **NCHW**、**BGR**、数值 **[0,1]**；后处理与 OpenCV `FaceDetectorYN` ONNX 版一致，框与关键点再按比例映射回 **原图坐标**。
- 比对：各图取 **置信度最高** 的一张脸，用 **5 点相似变换** 对齐到 **112×112 RGB**，按 SFace 约定转为 planar float blob，得到 **128 维** embedding；相似度为  
  `similarity = (rawCosine + 1) / 2`，其中 `rawCosine` 为两向量 L2 归一化后的余弦相似度，**约 ∈ [0,1]**。
- 业务异常（如未检测到人脸 `NO_FACE`）：抛出 `IllegalArgumentException`，由 `ApiExceptionAdvice` 转为 **HTTP 400**，body 形如 `{"message":"NO_FACE"}`。

## 目录结构

- `backend/`：后端 Spring Boot
- `frontend/`：前端 Vue3

## 后端启动（Windows / Linux）

**前置**：JDK 17、Maven 3.8+

```bash
cd backend
mvn -DskipTests spring-boot:run
```

- 健康检查：`GET http://localhost:8080/api/health`
- 默认端口：`8080`（可在 `application.yml` 中修改）

### 配置（`backend/src/main/resources/application.yml`）

| 配置项 | 含义 | 默认 |
|--------|------|------|
| `face.model.detect` | 检测 ONNX 的 classpath 路径 | `models/face_detection_yunet_2023mar.onnx` |
| `face.model.recognize` | 识别 ONNX 的 classpath 路径 | `models/face_recognition_sface_2021dec.onnx` |

模型文件需放在 `backend/src/main/resources/models/`，说明见仓库根目录 [models/README.md](models/README.md)。

## HTTP API（均为 `multipart/form-data`）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/face/detect` | 多脸检测，可选 landmarks、crop Base64 |
| `POST` | `/api/face/check` | 单图快速校验（有脸/单脸/分数） |
| `POST` | `/api/face/verify` | 双图 1:1 同人判定 |
| `POST` | `/api/face/verify/calibrate` | 用两对样本估算建议阈值 |

### Swagger / OpenAPI

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

### `POST /api/face/detect`

- **file**（必填）：图片文件
- 可选：`returnLandmarks`（默认 `true`）、`returnCrops`（默认 `true`）、`cropMaxSize`（默认 `256`，范围 32~1024）、`scoreThreshold`（默认 `0.9`）、`nmsThreshold`（默认 `0.3`）、`topK`（默认 `5000`）

```bash
curl -F "file=@test.jpg" "http://localhost:8080/api/face/detect"
```

响应字段：`faces[].bbox`（原图像素框）、`score`、`landmarks`（5 点）、`cropImageBase64`（PNG Base64）。

### `POST /api/face/check`

- **file**（必填）
- `threshold`（默认 `0.9`）：检测置信度下限
- `requireSingleFace`（默认 `true`）：为 true 时，高于 `threshold` 的检测数必须为 1

响应 `reason`：`OK` | `NO_FACE` | `MULTIPLE_FACES` | `LOW_SCORE`。

### `POST /api/face/verify`

- **file1**、**file2**（必填）
- `threshold`（默认 `0.95`）：`similarity >= threshold` 视为同人

无脸时 **400**，`message` 常为 `NO_FACE`。

### `POST /api/face/verify/calibrate`

- **same1**、**same2**：同人样本各一张  
- **diff1**、**diff2**：异人样本各一张  

返回同人/异人对的 `similarity` 与 `suggestedThreshold`（启发式，需结合实际数据调参）。

## 前端启动

**前置**：Node.js 18+

```bash
cd frontend
npm install
npm run dev
```

访问：`http://localhost:5173`

- 开发态：`vite.config` 将 `/api` **代理**到 `http://localhost:8080`。
- 后端 **CORS** 允许 `http://localhost:5173` 直连。  
- 上传请使用 `FormData`，**不要**手动设置 `Content-Type`（由浏览器带 multipart boundary）。

## 打包

```bash
cd backend && mvn -DskipTests package
cd ../frontend && npm run build
```

## 常见问题

### 1) 模型文件在哪里？

将 OpenCV Zoo 导出的 ONNX 放到 `backend/src/main/resources/models/`。未放置或路径错误时，应用在创建 `FaceSdk` Bean 阶段可能失败。说明与许可证见 [models/README.md](models/README.md)。

### 2) 比对返回 400 `NO_FACE`

某张图未检出人脸或脸过小/模糊。可换图或在前端对检测接口调低 `scoreThreshold` 做排查（比对链路内部检测参数固定为中等阈值）。

### 3) GitHub 上 ONNX 体积为 0 或无法下载

OpenCV Zoo 常用 **Git LFS**；请从 **Release 资源** 或完整 LFS clone 获取，或使用镜像站点。

## 许可证与商用说明

- 本项目示例代码以实用为目的；**模型权重**的商用许可以各模型发布方条款为准，正式产品请做法务确认。
- 依赖组件（Spring Boot、ONNX Runtime、DJL 等）各自遵循其开源协议。
