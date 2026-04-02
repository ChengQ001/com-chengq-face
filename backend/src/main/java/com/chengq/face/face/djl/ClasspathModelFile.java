package com.chengq.face.face.djl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;

/**
 * Copies a classpath ONNX resource to a temp file so native ORT can mmap it reliably.
 */
public final class ClasspathModelFile {

  private ClasspathModelFile() {}

  public static Path materialize(String classpathLocation) throws IOException {
    ClassPathResource res = new ClassPathResource(classpathLocation);
    if (!res.exists()) {
      throw new IOException("Missing model on classpath: " + classpathLocation);
    }
    String name = res.getFilename();
    if (name == null || name.isBlank()) {
      name = "model.onnx";
    }
    Path tmp = Files.createTempFile("face-onnx-", "-" + name);
    tmp.toFile().deleteOnExit();
    try (InputStream in = Objects.requireNonNull(res.getInputStream())) {
      Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    return tmp;
  }
}
