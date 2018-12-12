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

package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.async.AsyncDependencies;
import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.http.BlobProgressListener;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Pushes a BLOB to the target registry. */
class PushBlobStep implements AsyncStep<BlobDescriptor>, Callable<BlobDescriptor> {

  private static class ForwardingProgressListener implements BlobProgressListener {

    private final ProgressEventDispatcher progressEventDispatcher;

    private ForwardingProgressListener(ProgressEventDispatcher progressEventDispatcher) {
      this.progressEventDispatcher = progressEventDispatcher;
    }

    @Override
    public void handleByteCount(long byteCount) {
      progressEventDispatcher.dispatchProgress(byteCount);
    }

    @Override
    public Duration getDelayBetweenCallbacks() {
      return Duration.ofMillis(100);
    }
  }

  private static final String DESCRIPTION = "Pushing BLOB ";

  private final BuildConfiguration buildConfiguration;
  private final ProgressEventDispatcher.Factory progressEventDipatcherFactory;

  private final AuthenticatePushStep authenticatePushStep;
  private final BlobDescriptor blobDescriptor;
  private final Blob blob;

  private final ListenableFuture<BlobDescriptor> listenableFuture;

  PushBlobStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      ProgressEventDispatcher.Factory progressEventDipatcherFactory,
      AuthenticatePushStep authenticatePushStep,
      BlobDescriptor blobDescriptor,
      Blob blob) {
    this.buildConfiguration = buildConfiguration;
    this.progressEventDipatcherFactory = progressEventDipatcherFactory;
    this.authenticatePushStep = authenticatePushStep;
    this.blobDescriptor = blobDescriptor;
    this.blob = blob;

    listenableFuture =
        AsyncDependencies.using(listeningExecutorService)
            .addStep(authenticatePushStep)
            .whenAllSucceed(this);
  }

  @Override
  public ListenableFuture<BlobDescriptor> getFuture() {
    return listenableFuture;
  }

  @Override
  public BlobDescriptor call() throws IOException, RegistryException, ExecutionException {
    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDipatcherFactory.create(
                "push blob " + blobDescriptor.getDigest(), blobDescriptor.getSize());
        TimerEventDispatcher ignored =
            new TimerEventDispatcher(
                buildConfiguration.getEventDispatcher(), DESCRIPTION + blobDescriptor)) {
      RegistryClient registryClient =
          buildConfiguration
              .newTargetImageRegistryClientFactory()
              .setAuthorization(NonBlockingSteps.get(authenticatePushStep))
              .newRegistryClient();

      // check if the BLOB is available
      if (registryClient.checkBlob(blobDescriptor.getDigest()) != null) {
        buildConfiguration
            .getEventDispatcher()
            .dispatch(LogEvent.info("BLOB : " + blobDescriptor + " already exists on registry"));
        return blobDescriptor;
      }

      // todo: leverage cross-repository mounts
      registryClient.pushBlob(
          blobDescriptor.getDigest(),
          blob,
          null,
          new ForwardingProgressListener(progressEventDispatcher));

      return blobDescriptor;
    }
  }
}
