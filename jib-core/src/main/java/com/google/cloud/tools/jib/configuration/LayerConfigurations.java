/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

/** Defines a list of {@link LayerConfiguration}s. */
public class LayerConfigurations {

  private final ImmutableList<LayerConfiguration> layerConfigurations;
  private final ImmutableMap<String, LayerConfiguration> layerConfigurationMap;

  /**
   * Stores the {@link LayerConfiguration}s and allows for access by label. The labels should be unique, but if not, the last layer with a certain label will be referenced by that label.
   *
   * @param layerConfigurations the list of {@link LayerConfiguration}s
   */
  public LayerConfigurations(List<LayerConfiguration> layerConfigurations) {
    this.layerConfigurations = ImmutableList.copyOf(layerConfigurations);

    ImmutableMap.Builder<String, LayerConfiguration> layerConfigurationMapBuilder = ImmutableMap.builderWithExpectedSize(layerConfigurations.size());
    for (LayerConfiguration layerConfiguration : layerConfigurations) {
      layerConfigurationMapBuilder.put(layerConfiguration.getLabel(), layerConfiguration);
    }
    this.layerConfigurationMap = layerConfigurationMapBuilder.build();
  }

  public ImmutableList<LayerConfiguration> getLayerConfigurations() {
    return layerConfigurations;
  }

  public LayerConfiguration getByLabel(String label) {
    return layerConfigurationMap.get(label);
  }
}
