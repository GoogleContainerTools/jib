/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.image.json;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.cloud.tools.jib.json.JsonTemplate;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** A bundle of an image manifest list, manifests, and container configurations. */
public class ImageMetadataTemplate implements JsonTemplate {

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.CLASS,
      include = JsonTypeInfo.As.PROPERTY,
      property = "@class")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = OciIndexTemplate.class),
    @JsonSubTypes.Type(value = V22ManifestListTemplate.class),
  })
  @Nullable
  private ManifestTemplate manifestList;

  private List<ManifestAndConfigTemplate> manifestsAndConfigs = new ArrayList<>();

  @SuppressWarnings("unused")
  private ImageMetadataTemplate() {}

  public ImageMetadataTemplate(
      @Nullable ManifestTemplate manifestList,
      List<ManifestAndConfigTemplate> manifestsAndConfigs) {
    this.manifestList = manifestList;
    this.manifestsAndConfigs = manifestsAndConfigs;
  }

  @Nullable
  public ManifestTemplate getManifestList() {
    return manifestList;
  }

  public List<ManifestAndConfigTemplate> getManifestsAndConfigs() {
    return manifestsAndConfigs;
  }
}
