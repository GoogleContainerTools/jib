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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.JsonToImageTranslator;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.UnknownManifestFormatException;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Pulls the base image manifest. */
class PullBaseImageStep implements AsyncStep<Image<Layer>>, Callable<Image<Layer>> {

  private static final String DESCRIPTION = "Pulling base image manifest";

  private final BuildConfiguration buildConfiguration;
  private final AuthenticatePullStep authenticatePullStep;

  private final ListenableFuture<Image<Layer>> listenableFuture;

  PullBaseImageStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      AuthenticatePullStep authenticatePullStep) {
    this.buildConfiguration = buildConfiguration;
    this.authenticatePullStep = authenticatePullStep;

    listenableFuture =
        Futures.whenAllSucceed(authenticatePullStep.getFuture())
            .call(this, listeningExecutorService);
  }

  @Override
  public ListenableFuture<Image<Layer>> getFuture() {
    return listenableFuture;
  }

  @Override
  public Image<Layer> call()
      throws IOException, RegistryException, LayerPropertyNotFoundException,
          LayerCountMismatchException, ExecutionException {
    buildConfiguration
        .getBuildLogger()
        .lifecycle("Getting base image " + buildConfiguration.getBaseImageReference() + "...");

    try (Timer ignored = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      RegistryClient registryClient =
          new RegistryClient(
              NonBlockingSteps.get(authenticatePullStep),
              buildConfiguration.getBaseImageRegistry(),
              buildConfiguration.getBaseImageRepository());

      ManifestTemplate manifestTemplate =
          registryClient.pullManifest(buildConfiguration.getBaseImageTag());

      // TODO: Make schema version be enum.
      switch (manifestTemplate.getSchemaVersion()) {
        case 1:
          V21ManifestTemplate v21ManifestTemplate = (V21ManifestTemplate) manifestTemplate;
          return JsonToImageTranslator.toImage(v21ManifestTemplate);

        case 2:
          V22ManifestTemplate v22ManifestTemplate = (V22ManifestTemplate) manifestTemplate;
          if (v22ManifestTemplate.getContainerConfiguration() == null
              || v22ManifestTemplate.getContainerConfiguration().getDigest() == null) {
            throw new UnknownManifestFormatException(
                "Invalid container configuration in Docker V2.2 manifest: \n"
                    + Blobs.writeToString(JsonTemplateMapper.toBlob(v22ManifestTemplate)));
          }

          ByteArrayOutputStream containerConfigurationOutputStream = new ByteArrayOutputStream();
          registryClient.pullBlob(
              v22ManifestTemplate.getContainerConfiguration().getDigest(),
              containerConfigurationOutputStream);
          String containerConfigurationString =
              new String(containerConfigurationOutputStream.toByteArray(), StandardCharsets.UTF_8);

          ContainerConfigurationTemplate containerConfigurationTemplate =
              JsonTemplateMapper.readJson(
                  containerConfigurationString, ContainerConfigurationTemplate.class);
          return JsonToImageTranslator.toImage(v22ManifestTemplate, containerConfigurationTemplate);
      }

      throw new IllegalStateException("Unknown manifest schema version");
    }
  }
}
