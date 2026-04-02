package com.chengq.face.face;

import java.util.List;

/** 一次检测的汇总：原图宽高与按置信度排序的人脸列表（降序）。 */
public record DetectResult(
    int imageWidth,
    int imageHeight,
    List<FaceDetection> faces
) {}

