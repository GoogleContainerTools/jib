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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.DuplicateLayerException;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.ImageLayers;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.registry.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.NonexistentServerUrlDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.registry.RegistryException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** All the steps to build an image. */
public class BuildImageSteps {

  private final BuildConfiguration buildConfiguration;
  private final SourceFilesConfiguration sourceFilesConfiguration;
  private final Path cacheDirectory;

  public BuildImageSteps(
      BuildConfiguration buildConfiguration,
      SourceFilesConfiguration sourceFilesConfiguration,
      Path cacheDirectory) {
    this.buildConfiguration = buildConfiguration;
    this.sourceFilesConfiguration = sourceFilesConfiguration;
    this.cacheDirectory = cacheDirectory;
  }

  public void run()
      throws CacheMetadataCorruptedException, IOException, RegistryAuthenticationFailedException,
          RegistryException, DuplicateLayerException, LayerCountMismatchException,
          LayerPropertyNotFoundException, NonexistentServerUrlDockerCredentialHelperException,
          NonexistentDockerCredentialHelperException {
    try (Timer t = Timer.push("BuildImageSteps")) {

      try (Cache cache = Cache.init(cacheDirectory)) {
        try (Timer t2 = Timer.push("AuthenticatePullStep")) {
          // Authenticates base image pull.
          AuthenticatePullStep authenticatePullStep = new AuthenticatePullStep(buildConfiguration);
          Authorization pullAuthorization = authenticatePullStep.call();

          Timer.time("PullBaseImageStep");
          // Pulls the base image.
          PullBaseImageStep pullBaseImageStep =
              new PullBaseImageStep(buildConfiguration, pullAuthorization);
          Image baseImage = pullBaseImageStep.call();

          Timer.time("PullAndCacheBaseImageLayersStep");
          // Pulls and caches the base image layers.
          PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep =
              new PullAndCacheBaseImageLayersStep(
                  buildConfiguration, cache, pullAuthorization, baseImage);
          ImageLayers<CachedLayer> baseImageLayers = pullAndCacheBaseImageLayersStep.call();

          Timer.time("AuthenticatePushStep");
          // Authenticates push.
          AuthenticatePushStep authenticatePushStep = new AuthenticatePushStep(buildConfiguration);
          Authorization pushAuthorization = authenticatePushStep.call();

          Timer.time("PushBaseImageLayersStep");
          // Pushes the base image layers.
          PushBaseImageLayersStep pushBaseImageLayersStep =
              new PushBaseImageLayersStep(buildConfiguration, pushAuthorization, baseImageLayers);
          pushBaseImageLayersStep.call();

          Timer.time("BuildAndCacheApplicationLayersStep");
          BuildAndCacheApplicationLayersStep buildAndCacheApplicationLayersStep =
              new BuildAndCacheApplicationLayersStep(sourceFilesConfiguration, cache);
          ImageLayers<CachedLayer> applicationLayers = buildAndCacheApplicationLayersStep.call();

          Timer.time("PushApplicationLayerStep");
          // Pushes the application layers.
          PushApplicationLayersStep pushApplicationLayersStep =
              new PushApplicationLayersStep(
                  buildConfiguration, pushAuthorization, applicationLayers);
          pushApplicationLayersStep.call();

          Timer.time("PushImageStep");
          // Pushes the new image manifest.
          Image image =
              new Image()
                  .addLayers(baseImageLayers)
                  .addLayers(applicationLayers)
                  .setEntrypoint(getEntrypoint());
          PushImageStep pushImageStep =
              new PushImageStep(buildConfiguration, pushAuthorization, image);
          pushImageStep.call();

          System.out.println(getEntrypoint());
        }
      }
    } finally {
      Timer.print();
    }
  }

  /**
   * Gets the container entrypoint.
   *
   * <p>The entrypoint is {@code java -cp [classpaths] [main class]}.
   */
  private List<String> getEntrypoint() {
    List<String> classPaths = new ArrayList<>();
    classPaths.add(sourceFilesConfiguration.getDependenciesPathOnImage().resolve("*").toString());
    classPaths.add(sourceFilesConfiguration.getResourcesPathOnImage().toString());
    classPaths.add(sourceFilesConfiguration.getClassesPathOnImage().toString());

    String entrypoint = String.join(":", classPaths);

    return Arrays.asList("java", "-cp", entrypoint, buildConfiguration.getMainClass());
  }
}
