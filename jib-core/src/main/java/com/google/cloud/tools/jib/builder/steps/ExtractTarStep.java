/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.builder.steps.ExtractTarStep.LocalImage;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.docker.json.DockerManifestEntryTemplate;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.JsonToImageTranslator;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.tar.TarExtractor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

public class ExtractTarStep implements Callable<LocalImage> {

  static class LocalImage {
    Image baseImage;
    List<PreparedLayer> layers;

    LocalImage(Image baseImage, List<PreparedLayer> layers) {
      this.baseImage = baseImage;
      this.layers = layers;
    }
  }

  private final Path tarPath;
  private final BuildConfiguration buildConfiguration;

  ExtractTarStep(Path tarPath, BuildConfiguration buildConfiguration) {
    this.tarPath = tarPath;
    this.buildConfiguration = buildConfiguration;
  }

  @Override
  public LocalImage call() throws IOException {
    Path destination = buildConfiguration.getBaseImageLayersCache().getTemporaryDirectory();
    TarExtractor.extract(tarPath, destination);
    DockerManifestEntryTemplate manifest =
        JsonTemplateMapper.readJsonFromFile(
            destination.resolve("manifest.json"), DockerManifestEntryTemplate.class);
    ContainerConfigurationTemplate configuration =
        JsonTemplateMapper.readJsonFromFile(
            destination.resolve(manifest.getConfig()), ContainerConfigurationTemplate.class);

    Image image = JsonToImageTranslator.toImage(manifest, configuration);
    List<PreparedLayer> layers;

    return new LocalImage(image, layers);
  }
}
