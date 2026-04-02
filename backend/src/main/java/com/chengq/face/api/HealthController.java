package com.chengq.face.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 简单存活探针，供编排或前端轮询。 */
@RestController
public class HealthController {

  /** @return {@code ok}=true 与 UTC 时间戳字符串 */
  @GetMapping("/api/health")
  public Map<String, Object> health() {
    return Map.of("ok", true, "ts", Instant.now().toString());
  }
}

