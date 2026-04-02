package com.chengq.face.face.djl;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YuNetSmokeTest {

  @Test
  void printSessionIoAndRunTinyInput() throws Exception {
    Path p = ClasspathModelFile.materialize("models/face_detection_yunet_2023mar.onnx").toAbsolutePath();
    try (OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession s = env.createSession(p.toString(), new OrtSession.SessionOptions())) {
      System.out.println("INPUTS: " + s.getInputNames());
      System.out.println("OUTPUTS: " + s.getOutputNames());
      String in = s.getInputNames().iterator().next();
      var info = s.getInputInfo().get(in).getInfo();
      System.out.println("INPUT INFO: " + info);

      float[] blob = new float[1 * 3 * 640 * 640];
      try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(blob), new long[] {1, 3, 640, 640});
          OrtSession.Result r = s.run(Map.of(in, t))) {
        for (String out : s.getOutputNames()) {
          OnnxTensor ot = (OnnxTensor) r.get(out).get();
          System.out.println(out + " shape=" + java.util.Arrays.toString(ot.getInfo().getShape()) + " n=" + ot.getFloatBuffer().remaining());
        }
      }
    }
  }
}
