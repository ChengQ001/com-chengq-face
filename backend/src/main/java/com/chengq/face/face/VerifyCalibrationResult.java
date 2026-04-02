package com.chengq.face.face;

/**
 * 阈值标定：同人/异人样本的 similarity 与启发式建议阈值。
 *
 * @param note 人类可读说明（算法假设与调参提示）
 */
public record VerifyCalibrationResult(
    double sameSimilarity,
    double diffSimilarity,
    double suggestedThreshold,
    String note
) {}

