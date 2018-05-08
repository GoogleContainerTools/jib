/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

import com.google.cloud.tools.jib.json.JsonTemplate;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON Template for loadable Docker Manifest
 *
 * <p>Example manifest JSON:
 *
 * <pre>{@code
 * [{
 *   "Config":"config.json",
 *   "RepoTags":["repository:tag"]
 *   "Layers": [
 *     "eb05f3dbdb543cc610527248690575bacbbcebabe6ecf665b189cf18b541e3ca.tar.gz",
 *     "ba7c544469e514f1a9a4dec59ab640540d50992b288adbb34a1a63c45bf19a24.tar.gz",
 *     "15705ab016593987662839b40f5a22fd1032996c90808d4a1371eb46974017d5.tar.gz",
 *   ]
 * }]</pre>
 */
class DockerLoadManifestTemplate implements JsonTemplate {

  private final String config = "config.json";
  private final String[] repoTags = new String[1];
  private final List<String> layers = new ArrayList<>();

  public void setRepoTags(String repoTags) {
    this.repoTags[0] = repoTags;
  }

  public void addLayerFiles(List<String> layers) {
    this.layers.addAll(layers);
  }
}
