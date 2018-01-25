/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.crepecake.builder;

import com.google.cloud.tools.crepecake.Timer;
import com.google.cloud.tools.crepecake.cache.CachedLayer;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.image.DescriptorDigest;
import com.google.cloud.tools.crepecake.registry.RegistryClient;
import com.google.cloud.tools.crepecake.registry.RegistryException;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Pushes a BLOB to the target registry. */
class PushBlobStep implements Callable<Void> {

  private static String DESCRIPTION = "Pushing BLOB";

  private final BuildConfiguration buildConfiguration;
  private final Future<Authorization> pushAuthorizationFuture;
  private final Future<CachedLayer> pullLayerFuture;

  PushBlobStep(
      BuildConfiguration buildConfiguration,
      Future<Authorization> pushAuthorizationFuture,
      Future<CachedLayer> pullLayerFuture) {
    this.buildConfiguration = buildConfiguration;
    this.pushAuthorizationFuture = pushAuthorizationFuture;
    this.pullLayerFuture = pullLayerFuture;
  }

  /** Depends on {@code pushAuthorizationFuture} and {@code pullLayerFuture}. */
  @Override
  public Void call()
      throws IOException, RegistryException, ExecutionException, InterruptedException {
    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      RegistryClient registryClient =
          new RegistryClient(
                  pushAuthorizationFuture.get(),
                  buildConfiguration.getTargetServerUrl(),
                  buildConfiguration.getTargetImageName())
              .setTimer(timer);
      CachedLayer layer = pullLayerFuture.get();

      DescriptorDigest layerDigest = layer.getBlobDescriptor().getDigest();
      //      if (registryClient.checkBlob(layerDigest) != null) {
      //        return null;
      //      }

      registryClient.pushBlob(layerDigest, layer.getBlob());

      return null;
    }
  }
}
