/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.jib.configuration;

import com.google.cloud.tools.jib.global.JibSystemProperties;
import javax.annotation.Nullable;

/**
 * Examine the {@link BuildConfiguration} to see blob-pushes should request a {@code mount/from}. If
 * base and target images are in the same registry, then use mount/from to try mounting the BLOB
 * from the base image repository to the target image repository and possibly avoid having to push
 * the BLOB. See the <a
 * href="https://docs.docker.com/registry/spec/api/#cross-repository-blob-mount">Docker registry
 * specification</a> for details.
 */
public class CrossRepositoryBlobMountsSupport {

  /**
   * Determine the cross-repository blob mount location if applicable for the provided build
   * configuration.
   *
   * @param buildConfiguration the build configuration
   * @return the image name to be mounted or {@code null} if not applicable
   */
  @Nullable
  public static String getMountFrom(BuildConfiguration buildConfiguration) {
    if (!JibSystemProperties.useCrossRepositoryBlobMounts()) {
      return null;
    }
    boolean sameRegistry =
        buildConfiguration
            .getBaseImageConfiguration()
            .getImageRegistry()
            .equals(buildConfiguration.getTargetImageConfiguration().getImageRegistry());
    if (!sameRegistry) {
      return null;
    }
    return buildConfiguration.getBaseImageConfiguration().getImageRepository();
  }
}
