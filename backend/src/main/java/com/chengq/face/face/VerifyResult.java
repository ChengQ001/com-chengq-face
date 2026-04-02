package com.chengq.face.face;

/**
 * 1:1 比对结果。
 *
 * @param rawCosine    两个 L2 归一化向量的余弦相似度，范围约 [-1, 1]
 * @param similarity   映射到 [0,1] 的分数：{@code (rawCosine + 1) / 2}
 * @param threshold    调用方传入的判定阈值（与 similarity 同量纲）
 * @param samePerson   {@code similarity >= threshold}
 */
public record VerifyResult(
    double rawCosine,
    double similarity,
    double threshold,
    boolean samePerson
) {}

