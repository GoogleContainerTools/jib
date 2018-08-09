package com.google.cloud.tools.jib.builder.steps;

import com.google.cloud.tools.jib.async.AsyncStep;
import com.google.cloud.tools.jib.async.NonBlockingSteps;
import com.google.cloud.tools.jib.configuration.BuildConfiguration;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.concurrent.Callable;

/**
 * Decide whether to do the Cross-Repository BLOB Mount.
 *
 * <p>If base and target images are in the same registry and the credentials for the target image
 * works for the base image, then we can use mount/from to try mounting the BLOB from the base image
 * repository to the target image repository and possibly avoid having to push the BLOB. See
 * https://docs.docker.com/registry/spec/api/#cross-repository-blob-mount for details.
 */
public class DecideCrossRepositoryBlobMountStep implements AsyncStep<Boolean>, Callable<Boolean> {

  private final ListenableFuture<Boolean> listenableFuture;
  private final PullBaseImageStep baseImage;
  private final RetrieveRegistryCredentialsStep targetRegistryCredentials;

  DecideCrossRepositoryBlobMountStep(
      ListeningExecutorService listeningExecutorService,
      BuildConfiguration buildConfiguration,
      RetrieveRegistryCredentialsStep targetRegistryCredentials,
      PullBaseImageStep pullBaseImageStep) {
    this.targetRegistryCredentials = targetRegistryCredentials;
    this.baseImage = pullBaseImageStep;

    String baseRegistry = buildConfiguration.getBaseImageConfiguration().getImageRegistry();
    String targetRegistry = buildConfiguration.getTargetImageConfiguration().getImageRegistry();

    if (baseRegistry.equals(targetRegistry)) {
      listenableFuture =
          Futures.whenAllSucceed(
                  pullBaseImageStep.getFuture(), targetRegistryCredentials.getFuture())
              .call(this, listeningExecutorService);
    } else {
      listenableFuture = Futures.immediateFuture(false);
    }
  }

  @Override
  public Boolean call() throws Exception {
    Authorization target = NonBlockingSteps.get(targetRegistryCredentials);
    Authorization base = NonBlockingSteps.get(baseImage).getBaseImageAuthorization();
    return base == null || target.equals(base);
  }

  @Override
  public ListenableFuture<Boolean> getFuture() {
    return listenableFuture;
  }
}
