package com.chengq.face.api.dto;

/**
 * {@code POST /api/face/check} 的 JSON 响应。
 *
 * @param normalFace         是否通过校验（同时满足：有足够置信的人脸、张数约束、最高分达标）
 * @param threshold          请求传入的检测置信度下限
 * @param requireSingleFace  是否要求仅一张高于阈值的脸
 * @param faceCount          置信度不低于 {@code threshold} 的检测框数量
 * @param bestScore          上述框中的最高置信度，无人脸时为 null
 * @param reason             {@code OK} | {@code NO_FACE} | {@code MULTIPLE_FACES} | {@code LOW_SCORE}
 */
public record FaceCheckResponse(
    boolean normalFace,
    double threshold,
    boolean requireSingleFace,
    int faceCount,
    Double bestScore,
    String reason
) {}

