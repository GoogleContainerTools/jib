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

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Checks if a BLOb exists in a registry. */
class CheckBlobStep implements Callable<Boolean> {

  static ImmutableList<CheckBlobStep> makeList(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      Image baseImage,
      @Nullable Authorization pushAuthorization) {
    try (TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildConfiguration.getEventHandlers(), "Preparing blob checkers ");
        ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create(
                "preparing blob checkers", baseImage.getLayers().size())) {
      return baseImage
          .getLayers()
          .stream()
          .map(
              layer ->
                  new CheckBlobStep(
                      buildConfiguration,
                      progressEventDispatcher.newChildProducer(),
                      pushAuthorization,
                      layer.getBlobDescriptor().getDigest(),
                      true))
          .collect(ImmutableList.toImmutableList());
    }
  }

  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;
  @Nullable private final Authorization pushAuthorization;
  private final DescriptorDigest blobDigest;
  private final boolean ignoreError;

  CheckBlobStep(
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDispatcherFactory,
      @Nullable Authorization pushAuthorization,
      DescriptorDigest blobDigest,
      boolean ignoreError) {
    this.buildConfiguration = buildConfiguration;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
    this.pushAuthorization = pushAuthorization;
    this.blobDigest = blobDigest;
    this.ignoreError = ignoreError;
  }

  @Override
  public Boolean call() throws Exception {
    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("checking blob " + blobDigest, 1);
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildConfiguration.getEventHandlers(), "Checking blob " + blobDigest)) {

      RegistryClient registryClient =
          buildConfiguration
              .newTargetImageRegistryClientFactory()
              .setAuthorization(pushAuthorization)
              .newRegistryClient();
      return registryClient.checkBlob(blobDigest) != null;

    } catch (IOException | RegistryException ex) {
      if (ignoreError) {
        return false;
      }
      throw ex;
    }
  }
}
