/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.api;
// TODO: Move to com.google.cloud.tools.jib once that package is cleaned up.

import com.google.cloud.tools.jib.builder.BuildSteps;
import com.google.cloud.tools.jib.cache.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.cache.Caches.Initializer;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.configuration.CacheConfiguration;
import com.google.cloud.tools.jib.configuration.ImageConfiguration;

/** Represents a destination for the Jib-built image. */
interface TargetImage {

  // TODO: This should be removed when new cache mechanism is in place.
  static Initializer getCacheInitializer(BuildConfiguration buildConfiguration)
      throws CacheDirectoryCreationException {
    CacheConfiguration applicationLayersCacheConfiguration =
        buildConfiguration.getApplicationLayersCacheConfiguration() == null
            ? CacheConfiguration.makeTemporary()
            : buildConfiguration.getApplicationLayersCacheConfiguration();
    CacheConfiguration baseImageLayersCacheConfiguration =
        buildConfiguration.getBaseImageLayersCacheConfiguration() == null
            ? CacheConfiguration.forDefaultUserLevelCacheDirectory()
            : buildConfiguration.getBaseImageLayersCacheConfiguration();

    return new Initializer(
        baseImageLayersCacheConfiguration.getCacheDirectory(),
        applicationLayersCacheConfiguration.shouldEnsureOwnership(),
        applicationLayersCacheConfiguration.getCacheDirectory(),
        applicationLayersCacheConfiguration.shouldEnsureOwnership());
  }

  /**
   * Converts into an {@link ImageConfiguration}. For internal use only.
   *
   * @return an {@link ImageConfiguration}
   */
  ImageConfiguration toImageConfiguration();

  /**
   * Converts into {@link BuildSteps}. For internal use only.
   *
   * @param buildConfiguration the {@link BuildConfiguration} to use
   * @return {@link BuildSteps}
   * @throws CacheDirectoryCreationException TODO: To remove once new cache mechanism is in place
   */
  BuildSteps toBuildSteps(BuildConfiguration buildConfiguration)
      throws CacheDirectoryCreationException;
}
