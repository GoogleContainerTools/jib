/*
 * Copyright 2022 Google LLC.
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

import java.util.List;

/**
 * Parent class for manifest lists.
 *
 * @see V22ManifestListTemplate Docker V2.2 format
 * @see OciIndexTemplate OCI format
 */
public interface ManifestListTemplate extends ManifestTemplate {

  /**
   * Returns a list of digests for a specific platform found in the manifest list. see
   * <a>https://docs.docker.com/registry/spec/manifest-v2-2/#manifest-list</a>
   *
   * @param architecture the architecture of the target platform
   * @param os the os of the target platform
   * @return a list of matching digests
   */
  List<String> getDigestsForPlatform(String architecture, String os);
}
