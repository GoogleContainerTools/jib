/*
 * Copyright 2017 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.registry.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.jib.json.JsonTemplate;
import javax.annotation.Nullable;

// TODO: Should include detail field as well - need to have custom parser
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorEntryTemplate implements JsonTemplate {

  @Nullable private String code;
  @Nullable private String message;

  public ErrorEntryTemplate(String code, String message) {
    this.code = code;
    this.message = message;
  }

  /** Necessary for Jackson to create from JSON. */
  @SuppressWarnings("unused")
  private ErrorEntryTemplate() {}

  @Nullable
  public String getCode() {
    return code;
  }

  @Nullable
  public String getMessage() {
    return message;
  }
}
