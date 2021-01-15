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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.configuration.ImageConfiguration;
import java.nio.file.Path;
import java.util.Set;

/**
 * A simple test proxy to access the innards of Containerizer which are otherwise package private.
 */
public class ContainerizerTestProxy {

  private final Containerizer containerizer;

  public ContainerizerTestProxy(Containerizer containerizer) {
    this.containerizer = containerizer;
  }

  public boolean getAllowInsecureRegistries() {
    return containerizer.getAllowInsecureRegistries();
  }

  public boolean isOfflineMode() {
    return containerizer.isOfflineMode();
  }

  public String getToolName() {
    return containerizer.getToolName();
  }

  public String getToolVersion() {
    return containerizer.getToolVersion();
  }

  public boolean getAlwaysCacheBaseImage() {
    return containerizer.getAlwaysCacheBaseImage();
  }

  public String getDescription() {
    return containerizer.getDescription();
  }

  public ImageConfiguration getImageConfiguration() {
    return containerizer.getImageConfiguration();
  }

  public Path getBaseImageLayersCacheDirectory() {
    return containerizer.getBaseImageLayersCacheDirectory();
  }

  public Path getApplicationsLayersCacheDirectory() throws CacheDirectoryCreationException {
    return containerizer.getApplicationLayersCacheDirectory();
  }

  public Set<String> getAdditionalTags() {
    return containerizer.getAdditionalTags();
  }
}
