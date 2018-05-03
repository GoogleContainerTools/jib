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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.blob.BlobAndDigest;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.concurrent.Callable;

class PushContainerConfigurationStep implements Callable<ListenableFuture<BlobDescriptor>> {

  private static final String DESCRIPTION = "Pushing container configuration";

  private final BuildConfiguration buildConfiguration;
  private final ListenableFuture<Authorization> pushAuthorizationFuture;
  private final ListenableFuture<BlobAndDigest> blobAndDigestFuture;
  private final ListeningExecutorService listeningExecutorService;

  PushContainerConfigurationStep(
      BuildConfiguration buildConfiguration,
      ListenableFuture<Authorization> pushAuthorizationFuture,
      ListenableFuture<BlobAndDigest> blobAndDigestFuture,
      ListeningExecutorService listeningExecutorService) {
    this.buildConfiguration = buildConfiguration;
    this.pushAuthorizationFuture = pushAuthorizationFuture;
    this.blobAndDigestFuture = blobAndDigestFuture;
    this.listeningExecutorService = listeningExecutorService;
  }

  @Override
  public ListenableFuture<BlobDescriptor> call() {
    return listeningExecutorService.submit(
        () -> {
          try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
            RegistryClient registryClient =
                new RegistryClient(
                        NonBlockingFutures.get(pushAuthorizationFuture),
                        buildConfiguration.getTargetRegistry(),
                        buildConfiguration.getTargetRepository())
                    .setTimer(timer);

            // TODO: Use PushBlobStep.
            // Pushes the container configuration.
            BlobAndDigest blobAndDigest = blobAndDigestFuture.get();
            registryClient.pushBlob(
                blobAndDigest.getBlobDescriptor().getDigest(), blobAndDigest.getBlob());
            return blobAndDigest.getBlobDescriptor();
          }
        });
  }
}
