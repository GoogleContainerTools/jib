package com.google.cloud.tools.crepecake.registry.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.crepecake.json.JsonTemplate;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorEntryTemplate extends JsonTemplate {

  private String code;
  private String message;

  public ErrorEntryTemplate(String code, String message) {
    this.code = code;
    this.message = message;
  }

  private ErrorEntryTemplate() {}

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
