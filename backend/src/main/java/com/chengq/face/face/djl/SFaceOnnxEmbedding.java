package com.chengq.face.face.djl;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.nio.FloatBuffer;
import java.util.Map;

public final class SFaceOnnxEmbedding implements AutoCloseable {

  private final OrtEnvironment env;
  private final OrtSession session;
  private final String inputName;

  public SFaceOnnxEmbedding(OrtEnvironment env, OrtSession session) throws OrtException {
    this.env = env;
    this.session = session;
    this.inputName = session.getInputNames().iterator().next();
  }

  public static SFaceOnnxEmbedding load(OrtEnvironment env, String modelPath) throws OrtException {
    OrtSession session = env.createSession(modelPath, new OrtSession.SessionOptions());
    return new SFaceOnnxEmbedding(env, session);
  }

  @Override
  public void close() throws OrtException {
    session.close();
  }

  /** Expects OpenCV-consistent RGB planar float blob [1,3,112,112]. */
  public float[] embed(float[] nchwRgb112) throws OrtException {
    long[] shape = {1, 3, 112, 112};
    try (OnnxTensor in = OnnxTensor.createTensor(env, FloatBuffer.wrap(nchwRgb112), shape);
        OrtSession.Result r = session.run(Map.of(inputName, in))) {
      String outName = session.getOutputNames().iterator().next();
      OnnxTensor out = (OnnxTensor) r.get(outName).get();
      FloatBuffer fb = out.getFloatBuffer();
      float[] emb = new float[fb.remaining()];
      fb.get(emb);
      return emb;
    }
  }

  public float[] embedAlignedFaceRgb(byte[] rgb112Interleaved) throws OrtException {
    return embed(AlignAndEmbed.alignedRgbToSfaceBlob(rgb112Interleaved));
  }
}
