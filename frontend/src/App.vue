<template>
  <div class="page">
    <div class="header">
      <div class="title">人脸演示（检测 + 比对）</div>
      <div class="sub">检测：上传 1 张图并画框；比对：上传 2 张图计算相似度。参数名括号内为后端 API 字段，便于对接文档。</div>
    </div>

    <div class="pageLayout">
      <!-- 区块一：单图检测（预览 + 参数 + 结果同卡，自上而下浏览） -->
      <el-card class="sectionCard" shadow="hover">
        <template #header>
          <div class="sectionHead">
            <div class="sectionHeadText">
              <span class="sectionTitle">① 单图检测</span>
              <span class="sectionSub en">POST /api/face/detect · /check</span>
            </div>
            <el-button type="primary" :loading="loading" :disabled="!file" @click="runDetect">开始检测</el-button>
          </div>
        </template>

        <div class="detectSplit">
          <div class="detectPane detectPane--visual">
            <div class="paneLabel">图片与预览</div>
            <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onFileChange">
              <el-button type="default">选择图片</el-button>
            </el-upload>
            <div class="previewWrap previewWrap--section">
              <div v-if="!previewUrl" class="empty">选择图片后显示；点击「开始检测」叠加检测框与关键点</div>
              <div v-else class="stage">
                <img ref="imgRef" class="img" :src="previewUrl" @load="redraw" />
                <canvas ref="canvasRef" class="canvas"></canvas>
              </div>
            </div>
          </div>

          <div class="detectPane detectPane--params">
            <div class="paneLabel">选项与阈值</div>
            <div class="checkboxBlock">
              <el-checkbox v-model="returnLandmarks">
                返回 5 点关键点 <span class="en">(returnLandmarks)</span>
              </el-checkbox>
              <p class="paramHint">是否在结果中带眼角、鼻尖等坐标。</p>
              <el-checkbox v-model="returnCrops">
                返回裁剪缩略图 <span class="en">(returnCrops)</span>
              </el-checkbox>
              <p class="paramHint">Base64 小图；关闭可减小响应体积。</p>
              <el-checkbox v-model="requireSingleFace">
                合规检查要求仅 1 张人脸 <span class="en">(requireSingleFace)</span>
              </el-checkbox>
              <p class="paramHint">配合 <span class="en">/api/face/check</span>：是否必须单人脸才算通过。</p>
            </div>
            <div class="paramGrid2">
              <div class="paramBlock">
                <div class="paramTitle">裁剪最大边长 <span class="en">(cropMaxSize)</span></div>
                <el-input-number v-model="cropMaxSize" :min="32" :max="1024" class="paramInputWide" />
                <p class="paramHint">开启返回裁剪图时，缩略图最长边上限（像素）。</p>
              </div>
              <div class="paramBlock">
                <div class="paramTitle">合规置信度 <span class="en">(normalThreshold)</span></div>
                <el-input-number v-model="normalThreshold" :min="0" :max="1" :step="0.01" :precision="2" class="paramInputWide" />
                <p class="paramHint">Check 接口里计为有效人脸的分数下限。</p>
              </div>
              <div class="paramBlock">
                <div class="paramTitle">检测置信度 <span class="en">(scoreThreshold)</span></div>
                <el-input-number v-model="scoreThreshold" :min="0" :max="1" :step="0.05" :precision="2" class="paramInputWide" />
                <p class="paramHint">检测框保留阈值；调高减少误检。</p>
              </div>
              <div class="paramBlock">
                <div class="paramTitle">NMS 阈值 <span class="en">(nmsThreshold)</span></div>
                <el-input-number v-model="nmsThreshold" :min="0" :max="1" :step="0.05" :precision="2" class="paramInputWide" />
                <p class="paramHint">重叠框合并程度（IoU）。</p>
              </div>
            </div>
          </div>
        </div>

        <el-alert v-if="checkWarning" type="warning" :closable="false" :title="checkWarning" class="alertTight" />
        <el-alert v-if="error" type="error" :closable="false" :title="error" class="alertTight" />

        <el-divider content-position="left">
          <span class="dividerText">检测结果</span>
        </el-divider>

        <div class="resultSection">
          <div v-if="!result" class="empty muted">点击「开始检测」后在此展示摘要与人脸列表</div>
          <template v-else>
            <div class="resultSummary">
              <span class="pill">原图 {{ result.imageWidth }}×{{ result.imageHeight }}</span>
              <span class="pill pill--accent">人脸 {{ result.faces.length }} 个</span>
            </div>
            <div v-if="checkResult" class="checkResult">
              <div>
                合规 <span class="en">(normalFace)</span>：
                <b :style="{ color: checkResult.normalFace ? '#16a34a' : '#dc2626' }">{{ checkResult.normalFace ? '通过' : '未通过' }}</b>
                <span class="small" style="margin-left: 8px;">（threshold={{ checkResult.threshold.toFixed(2) }}，单人={{ checkResult.requireSingleFace }}）</span>
              </div>
              <div class="small" style="margin-top: 6px;">
                faceCount={{ checkResult.faceCount }}，bestScore={{ checkResult.bestScore == null ? '-' : checkResult.bestScore.toFixed(3) }}，
                reason={{ reasonZh(checkResult.reason) }}
              </div>
            </div>
            <div class="faces faces--grid">
              <div v-for="(f, idx) in result.faces" :key="idx" class="faceCard">
                <div class="faceInfo">
                  <div><b>#{{ idx + 1 }}</b> score={{ f.score.toFixed(3) }}</div>
                  <div class="small">bbox ({{ f.bbox.x }},{{ f.bbox.y }}) {{ f.bbox.width }}×{{ f.bbox.height }}</div>
                </div>
                <img v-if="f.cropImageBase64" class="crop" :src="toDataUrl(f.cropImageBase64)" alt="crop" />
              </div>
            </div>
          </template>
        </div>
      </el-card>

      <!-- 区块二：双图比对 + 标定 -->
      <el-card class="sectionCard" shadow="hover">
        <template #header>
          <div class="sectionHead">
            <div class="sectionHeadText">
              <span class="sectionTitle">② 双图比对</span>
              <span class="sectionSub en">POST /api/face/verify · /verify/calibrate</span>
            </div>
          </div>
        </template>

        <div class="paneLabel">上传两张人脸图并计算相似度</div>
        <div class="verifyPair">
          <div class="verifyTile">
            <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onFileAChange">
              <el-button>图 A <span class="en">(file1)</span></el-button>
            </el-upload>
            <div class="thumbBox">
              <img v-if="previewA" class="thumbLg" :src="previewA" alt="A" />
              <span v-else class="thumbPlaceholder">未选择</span>
            </div>
          </div>
          <div class="verifyTile">
            <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onFileBChange">
              <el-button>图 B <span class="en">(file2)</span></el-button>
            </el-upload>
            <div class="thumbBox">
              <img v-if="previewB" class="thumbLg" :src="previewB" alt="B" />
              <span v-else class="thumbPlaceholder">未选择</span>
            </div>
          </div>
        </div>

        <div class="verifyActions">
          <div class="paramBlock paramBlock--inline">
            <div class="paramTitle">判定阈值 <span class="en">(threshold)</span></div>
            <el-input-number v-model="verifyThreshold" :min="0" :max="1" :step="0.01" :precision="2" class="paramInputWide" />
          </div>
          <el-button type="success" :loading="verifying" :disabled="!fileA || !fileB" @click="runVerify">计算相似度</el-button>
        </div>
        <p class="paramHint">similarity = (rawCosine+1)/2；≥ threshold 视为同人。</p>

        <div v-if="verifyResult" class="verifyResultBox">
          <div>rawCosine <b>{{ verifyResult.rawCosine.toFixed(4) }}</b></div>
          <div>similarity <b>{{ verifyResult.similarity.toFixed(4) }}</b>（threshold {{ verifyResult.threshold.toFixed(2) }}）</div>
          <div>
            结论：
            <b :style="{ color: verifyResult.samePerson ? '#16a34a' : '#dc2626' }">{{ verifyResult.samePerson ? '同人' : '不同人' }}</b>
          </div>
        </div>
        <el-alert v-if="verifyError" type="error" :closable="false" :title="verifyError" class="alertTight" />

        <el-divider content-position="left">
          <span class="dividerText">阈值标定（可选）</span>
        </el-divider>
        <p class="paramHint" style="margin-bottom: 12px;">各上传一对「同人」与「异人」样本，获取建议阈值 <span class="en">suggestedThreshold</span>。</p>

        <div class="calibGrid">
          <div class="calibCell">
            <div class="small">同人 A <span class="en">(same1)</span></div>
            <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onSameAChange"><el-button size="small">选择</el-button></el-upload>
          </div>
          <div class="calibCell">
            <div class="small">同人 B <span class="en">(same2)</span></div>
            <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onSameBChange"><el-button size="small">选择</el-button></el-upload>
          </div>
          <div class="calibCell">
            <div class="small">异人 A <span class="en">(diff1)</span></div>
            <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onDiffAChange"><el-button size="small">选择</el-button></el-upload>
          </div>
          <div class="calibCell">
            <div class="small">异人 B <span class="en">(diff2)</span></div>
            <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onDiffBChange"><el-button size="small">选择</el-button></el-upload>
          </div>
        </div>
        <el-button
          class="calibBtn"
          size="small"
          :loading="calibrating"
          :disabled="!sameA || !sameB || !diffA || !diffB"
          @click="runCalibrate"
        >
          计算建议阈值
        </el-button>

        <div v-if="calibrationResult" class="calibOut">
          <div class="small">
            sameSimilarity={{ calibrationResult.sameSimilarity.toFixed(4) }}，diffSimilarity={{ calibrationResult.diffSimilarity.toFixed(4) }}
          </div>
          <div>suggestedThreshold <b>{{ calibrationResult.suggestedThreshold.toFixed(4) }}</b></div>
          <div class="paramHint">note：{{ calibrationResult.note }}</div>
          <el-button size="small" style="margin-top: 8px;" @click="applySuggestedThreshold">应用到比对阈值</el-button>
        </div>
        <el-alert v-if="calibrationError" type="error" :closable="false" :title="calibrationError" class="alertTight" />
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import {
  calibrateVerifyThreshold,
  checkFace,
  detectFace,
  formatApiError,
  verifyFace,
  type FaceCheckResponse,
  type VerifyCalibrationResponse,
  type VerifyResponse,
} from './api'
import type { DetectResponse } from './types'

const file = ref<File | null>(null)
const previewUrl = ref<string | null>(null)
const result = ref<DetectResponse | null>(null)
const checkResult = ref<FaceCheckResponse | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const checkWarning = ref<string | null>(null)

const fileA = ref<File | null>(null)
const fileB = ref<File | null>(null)
const previewA = ref<string | null>(null)
const previewB = ref<string | null>(null)
const verifying = ref(false)
const verifyError = ref<string | null>(null)
const verifyResult = ref<VerifyResponse | null>(null)
const verifyThreshold = ref(0.95)
const calibrating = ref(false)
const calibrationError = ref<string | null>(null)
const calibrationResult = ref<VerifyCalibrationResponse | null>(null)
const sameA = ref<File | null>(null)
const sameB = ref<File | null>(null)
const diffA = ref<File | null>(null)
const diffB = ref<File | null>(null)

const returnLandmarks = ref(true)
const returnCrops = ref(true)
const cropMaxSize = ref(256)
const scoreThreshold = ref(0.9)
const nmsThreshold = ref(0.3)
const normalThreshold = ref(0.9)
const requireSingleFace = ref(true)

const imgRef = ref<HTMLImageElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)

const reasonMap: Record<string, string> = {
  OK: '满足阈值与人数要求',
  NO_FACE: '未检测到达到阈值的人脸',
  MULTIPLE_FACES: '达到阈值的人脸多于 1 个',
  LOW_SCORE: '得分未达到阈值',
}

function reasonZh(code: string) {
  return reasonMap[code] ? `${reasonMap[code]} (${code})` : code
}

function onFileChange(uploadFile: any) {
  const f: File | undefined = uploadFile?.raw
  if (!f) return
  file.value = f
  result.value = null
  checkResult.value = null
  checkWarning.value = null
  error.value = null
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = URL.createObjectURL(f)
}

function onFileAChange(uploadFile: any) {
  const f: File | undefined = uploadFile?.raw
  if (!f) return
  fileA.value = f
  verifyResult.value = null
  verifyError.value = null
  if (previewA.value) URL.revokeObjectURL(previewA.value)
  previewA.value = URL.createObjectURL(f)
}

function onFileBChange(uploadFile: any) {
  const f: File | undefined = uploadFile?.raw
  if (!f) return
  fileB.value = f
  verifyResult.value = null
  verifyError.value = null
  if (previewB.value) URL.revokeObjectURL(previewB.value)
  previewB.value = URL.createObjectURL(f)
}

function onSameAChange(uploadFile: any) {
  const f: File | undefined = uploadFile?.raw
  if (!f) return
  sameA.value = f
  calibrationResult.value = null
  calibrationError.value = null
}

function onSameBChange(uploadFile: any) {
  const f: File | undefined = uploadFile?.raw
  if (!f) return
  sameB.value = f
  calibrationResult.value = null
  calibrationError.value = null
}

function onDiffAChange(uploadFile: any) {
  const f: File | undefined = uploadFile?.raw
  if (!f) return
  diffA.value = f
  calibrationResult.value = null
  calibrationError.value = null
}

function onDiffBChange(uploadFile: any) {
  const f: File | undefined = uploadFile?.raw
  if (!f) return
  diffB.value = f
  calibrationResult.value = null
  calibrationError.value = null
}

function toDataUrl(b64: string) {
  return `data:image/png;base64,${b64}`
}

function redraw() {
  const img = imgRef.value
  const canvas = canvasRef.value
  if (!img || !canvas) return
  const rect = img.getBoundingClientRect()
  canvas.width = Math.round(rect.width)
  canvas.height = Math.round(rect.height)
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  if (!result.value) return
  const sx = canvas.width / result.value.imageWidth
  const sy = canvas.height / result.value.imageHeight
  ctx.lineWidth = 2
  ctx.strokeStyle = '#00c2ff'
  ctx.fillStyle = '#00c2ff'
  for (const f of result.value.faces) {
    ctx.strokeRect(f.bbox.x * sx, f.bbox.y * sy, f.bbox.width * sx, f.bbox.height * sy)
    if (f.landmarks && f.landmarks.length) {
      for (const p of f.landmarks) {
        const x = p.x * sx
        const y = p.y * sy
        ctx.beginPath()
        ctx.arc(x, y, 3, 0, Math.PI * 2)
        ctx.fill()
      }
    }
  }
}

async function runDetect() {
  if (!file.value) return
  loading.value = true
  error.value = null
  checkWarning.value = null
  try {
    const res = await detectFace(file.value, {
      returnLandmarks: returnLandmarks.value,
      returnCrops: returnCrops.value,
      cropMaxSize: cropMaxSize.value,
      scoreThreshold: scoreThreshold.value,
      nmsThreshold: nmsThreshold.value,
    })
    result.value = res
    await nextTick()
    redraw()
    try {
      checkResult.value = await checkFace(file.value, normalThreshold.value, requireSingleFace.value)
    } catch (ce) {
      checkResult.value = null
      checkWarning.value = `合规检查未执行：${formatApiError(ce)}`
    }
  } catch (e: unknown) {
    error.value = formatApiError(e)
  } finally {
    loading.value = false
  }
}

async function runVerify() {
  if (!fileA.value || !fileB.value) return
  verifying.value = true
  verifyError.value = null
  try {
    verifyResult.value = await verifyFace(fileA.value, fileB.value, verifyThreshold.value)
  } catch (e: unknown) {
    verifyError.value = formatApiError(e)
  } finally {
    verifying.value = false
  }
}

function applySuggestedThreshold() {
  if (!calibrationResult.value) return
  verifyThreshold.value = Number(calibrationResult.value.suggestedThreshold.toFixed(2))
}

async function runCalibrate() {
  if (!sameA.value || !sameB.value || !diffA.value || !diffB.value) return
  calibrating.value = true
  calibrationError.value = null
  try {
    calibrationResult.value = await calibrateVerifyThreshold(sameA.value, sameB.value, diffA.value, diffB.value)
  } catch (e: unknown) {
    calibrationError.value = formatApiError(e)
  } finally {
    calibrating.value = false
  }
}
</script>

<style scoped>
.page {
  padding: 20px 16px 32px;
  font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Arial, "Noto Sans", "PingFang SC";
  color: #111827;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  min-height: 100vh;
}
.header { margin-bottom: 20px; max-width: 960px; margin-left: auto; margin-right: auto; }
.title { font-size: 20px; font-weight: 700; letter-spacing: -0.02em; }
.sub { font-size: 13px; color: #64748b; margin-top: 6px; line-height: 1.5; }

.pageLayout {
  max-width: 960px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.sectionCard {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}
.sectionCard :deep(.el-card__header) {
  padding: 14px 18px;
  background: linear-gradient(180deg, #fff 0%, #f8fafc 100%);
  border-bottom: 1px solid #e2e8f0;
}
.sectionCard :deep(.el-card__body) {
  padding: 18px;
}

.sectionHead {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.sectionHeadText { display: flex; flex-direction: column; gap: 2px; }
.sectionTitle { font-size: 16px; font-weight: 700; color: #0f172a; }
.sectionSub { font-size: 11px; color: #64748b; }

.detectSplit {
  display: grid;
  gap: 20px;
  grid-template-columns: 1fr;
}
@media (min-width: 880px) {
  .detectSplit {
    grid-template-columns: minmax(260px, 380px) 1fr;
    align-items: start;
  }
}

.detectPane { display: flex; flex-direction: column; gap: 10px; }
.detectPane--visual {
  padding: 12px;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}
.detectPane--params {
  padding: 4px 0 0;
}
.paneLabel {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.checkboxBlock {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 14px;
}
.checkboxBlock .paramHint { margin-bottom: 8px; }

.paramGrid2 {
  display: grid;
  gap: 14px;
  grid-template-columns: 1fr;
}
@media (min-width: 560px) {
  .paramGrid2 { grid-template-columns: 1fr 1fr; }
}

.paramBlock { display: grid; gap: 6px; }
.paramBlock--inline { margin: 0; }
.paramTitle { font-size: 13px; font-weight: 600; color: #1e293b; }
.paramInputWide { width: 100%; max-width: 160px; }
.paramHint { font-size: 12px; color: #64748b; line-height: 1.45; margin: 0; }
.en { font-size: 12px; font-weight: 500; color: #2563eb; }

.previewWrap {
  min-height: 200px;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  border: 1px dashed #cbd5e1;
}
.previewWrap--section {
  min-height: 240px;
  max-height: 420px;
}
.previewWrap--section .stage {
  max-height: 400px;
  overflow: auto;
}
.previewWrap--section .img {
  max-height: 380px;
  object-fit: contain;
  background: #e2e8f0;
}

.empty { color: #64748b; font-size: 13px; padding: 16px; text-align: center; }
.empty.muted { padding: 24px; background: #f8fafc; border-radius: 8px; border: 1px dashed #e2e8f0; }
.stage { position: relative; width: 100%; }
.img { width: 100%; height: auto; display: block; border-radius: 6px; }
.canvas { position: absolute; inset: 0; pointer-events: none; }

.alertTight { margin-top: 12px; }
.dividerText { font-size: 13px; font-weight: 600; color: #475569; }

.resultSection { margin-top: 4px; }
.resultSummary { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.pill {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
}
.pill--accent {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
}

.checkResult {
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #fafafa;
  border: 1px solid #e5e7eb;
}
.faces--grid {
  display: grid;
  gap: 12px;
  grid-template-columns: 1fr;
}
@media (min-width: 520px) {
  .faces--grid { grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
}
.faceCard {
  display: grid;
  grid-template-columns: 1fr 88px;
  gap: 10px;
  align-items: center;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}
.faceInfo { font-size: 13px; }
.small { font-size: 12px; color: #64748b; }
.crop {
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.verifyPair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 8px;
}
.verifyTile { display: flex; flex-direction: column; gap: 8px; }
.thumbBox {
  height: 160px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.thumbLg {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumbPlaceholder { font-size: 12px; color: #94a3b8; }

.verifyActions {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 14px;
  margin-top: 16px;
}
.verifyResultBox {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 10px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  font-size: 14px;
  line-height: 1.6;
}

.calibGrid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
@media (min-width: 640px) {
  .calibGrid { grid-template-columns: repeat(4, 1fr); }
}
.calibCell {
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fafafa;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.calibBtn { margin-top: 12px; }
.calibOut {
  margin-top: 12px;
  padding: 12px;
  border-radius: 8px;
  background: #fffbeb;
  border: 1px solid #fde68a;
}
</style>
