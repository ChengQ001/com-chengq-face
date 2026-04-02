package com.chengq.face.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将业务参数类异常转为 HTTP 400 JSON，便于前端与调用方统一处理。
 * <p>例如人脸比对无脸时 {@code NO_FACE}。
 */
@RestControllerAdvice
public class ApiExceptionAdvice {

  /** {@code {"message": "<异常消息>"}} */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    String msg = e.getMessage() != null ? e.getMessage() : "Bad request";
    return ResponseEntity.badRequest().body(Map.of("message", msg));
  }
}
