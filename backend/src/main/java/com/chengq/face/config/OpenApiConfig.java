package com.chengq.face.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger）文档基础配置。
 *
 * <p>springdoc 会自动扫描 Spring MVC Controller 并生成 `/v3/api-docs`，
 * Swagger UI 默认入口为 `/swagger-ui/index.html`。
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("com-chengq-face API")
                .version("1.0")
                .description("YuNet face detection + SFace verification demo backend")
                .contact(new Contact().name("com.chengq")));
  }
}

