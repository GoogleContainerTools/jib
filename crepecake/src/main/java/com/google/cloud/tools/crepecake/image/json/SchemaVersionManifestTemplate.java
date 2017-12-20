/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.crepecake.image.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.crepecake.json.JsonTemplate;

/** JSON template for just the {@code schemaVersion} field of image manifests. A {@code schemaVersion} of {@code 1} indicates that the JSON should be parsed as {@link V21ManifestTemplate}, whereas a {@code schemaVersion] of {@code 2} indicates that the JSON should be parsed as {@link V22ManifestTemplate}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaVersionManifestTemplate extends JsonTemplate {

  private int schemaVersion;

  public int getSchemaVersion() {
    return schemaVersion;
  }
}
