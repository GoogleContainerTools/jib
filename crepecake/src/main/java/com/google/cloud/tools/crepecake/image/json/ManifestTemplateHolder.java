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

import com.google.cloud.tools.crepecake.json.JsonTemplateMapper;
import com.google.common.base.Preconditions;
import java.io.IOException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Holds one of {@link V21ManifestTemplate} or {@link V22ManifestTemplate}. */
public class ManifestTemplateHolder {

  @Nullable private final V21ManifestTemplate v21ManifestTemplate;

  @Nullable private final V22ManifestTemplate v22ManifestTemplate;

  /** Instantiates by reading the {@code jsonString}. */
  public static ManifestTemplateHolder fromJson(String jsonString)
      throws IOException, UnknownManifestFormatException {
    SchemaVersionManifestTemplate schemaVersionManifestTemplate =
        JsonTemplateMapper.readJson(jsonString, SchemaVersionManifestTemplate.class);

    switch (schemaVersionManifestTemplate.getSchemaVersion()) {
      case 1:
        return new ManifestTemplateHolder(
            JsonTemplateMapper.readJson(jsonString, V21ManifestTemplate.class));

      case 2:
        return new ManifestTemplateHolder(
            JsonTemplateMapper.readJson(jsonString, V22ManifestTemplate.class));

      default:
        throw new UnknownManifestFormatException(
            "Unknown schemaVersion: " + schemaVersionManifestTemplate.getSchemaVersion());
    }
  }

  private ManifestTemplateHolder(@Nonnull V21ManifestTemplate v21ManifestTemplate) {
    this.v21ManifestTemplate = v21ManifestTemplate;
    this.v22ManifestTemplate = null;
  }

  private ManifestTemplateHolder(@Nonnull V22ManifestTemplate v22ManifestTemplate) {
    this.v21ManifestTemplate = null;
    this.v22ManifestTemplate = v22ManifestTemplate;
  }

  public boolean isV21() {
    return null != v21ManifestTemplate;
  }

  public boolean isV22() {
    return null != v22ManifestTemplate;
  }

  /** Only call if {@link #isV21} is true. */
  public V21ManifestTemplate getV21ManifestTemplate() {
    return Preconditions.checkNotNull(v21ManifestTemplate);
  }

  /** Only call if {@link #isV22} is true. */
  public V22ManifestTemplate getV22ManifestTemplate() {
    return Preconditions.checkNotNull(v22ManifestTemplate);
  }
}
