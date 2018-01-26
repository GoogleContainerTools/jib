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

import com.google.cloud.tools.crepecake.cache.Cache;
import com.google.cloud.tools.crepecake.cache.CacheMetadataCorruptedException;
import com.google.cloud.tools.crepecake.cache.CachedLayer;
import com.google.cloud.tools.crepecake.http.Authorization;
import com.google.cloud.tools.crepecake.image.DuplicateLayerException;
import com.google.cloud.tools.crepecake.image.Image;
import com.google.cloud.tools.crepecake.image.ImageLayers;
import com.google.cloud.tools.crepecake.image.LayerCountMismatchException;
import com.google.cloud.tools.crepecake.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.crepecake.registry.LocalRegistry;
import com.google.cloud.tools.crepecake.registry.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.crepecake.registry.NonexistentServerUrlDockerCredentialHelperException;
import com.google.cloud.tools.crepecake.registry.RegistryAuthenticationFailedException;
import com.google.cloud.tools.crepecake.registry.RegistryException;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Integration tests for various build steps. */
public class BuildImageStepsIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  @Rule public TemporaryFolder temporaryCacheDirectory = new TemporaryFolder();

  @Test
  public void testSteps()
      throws DuplicateLayerException, LayerPropertyNotFoundException, RegistryException,
          LayerCountMismatchException, IOException, CacheMetadataCorruptedException,
          RegistryAuthenticationFailedException,
          NonexistentServerUrlDockerCredentialHelperException,
          NonexistentDockerCredentialHelperException, URISyntaxException, InterruptedException {
    SourceFilesConfiguration sourceFilesConfiguration = new TestSourceFilesConfiguration();
    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder()
            .setBaseImageServerUrl("gcr.io")
            .setBaseImageName("distroless/java")
            .setBaseImageTag("latest")
            .setTargetServerUrl("localhost:5000")
            .setTargetImageName("testimage")
            .setTargetTag("testtag")
            .setCredentialHelperName("gcloud")
            .setMainClass("HelloWorld")
            .build();
    try (Cache cache = Cache.init(temporaryCacheDirectory.newFolder().toPath())) {
      // Authenticates base image pull.
      AuthenticatePullStep authenticatePullStep = new AuthenticatePullStep(buildConfiguration);
      Authorization pullAuthorization = authenticatePullStep.call();

      // Pulls the base image.
      PullBaseImageStep pullBaseImageStep =
          new PullBaseImageStep(buildConfiguration, pullAuthorization);
      Image baseImage = pullBaseImageStep.call();

      // Pulls and caches the base image layers.
      PullAndCacheBaseImageLayersStep pullAndCacheBaseImageLayersStep =
          new PullAndCacheBaseImageLayersStep(
              buildConfiguration, cache, pullAuthorization, baseImage);
      ImageLayers<CachedLayer> baseImageLayers = pullAndCacheBaseImageLayersStep.call();

      // TODO: Assert base image layers cached.

      // TODO: Set up authorization and mock a credential helper for the local registry.
      // Authenticates push.
      //    AuthenticatePushStep authenticatePushStep = new AuthenticatePushStep(buildConfiguration);
      //    Authorization pushAuthorization = authenticatePushStep.run(null);

      // Pushes the base image layers.
      PushBaseImageLayersStep pushBaseImageLayersStep =
          new PushBaseImageLayersStep(buildConfiguration, null, baseImageLayers);
      pushBaseImageLayersStep.call();

      BuildAndCacheApplicationLayersStep buildAndCacheApplicationLayersStep =
          new BuildAndCacheApplicationLayersStep(sourceFilesConfiguration, cache);
      ImageLayers<CachedLayer> applicationLayers = buildAndCacheApplicationLayersStep.call();

      // TODO: Assert application layers cached.

      // Pushes the application layers.
      PushApplicationLayersStep pushApplicationLayersStep =
          new PushApplicationLayersStep(buildConfiguration, null, applicationLayers);
      pushApplicationLayersStep.call();

      // Pushes the new image manifest.
      Image image =
          new Image()
              .addLayers(baseImageLayers)
              .addLayers(applicationLayers)
              .setEntrypoint(
                  getEntrypoint(sourceFilesConfiguration, buildConfiguration.getMainClass()));
      PushImageStep pushImageStep = new PushImageStep(buildConfiguration, null, image);
      pushImageStep.call();

      // TODO: Put this in a utility function.
      Runtime.getRuntime().exec("docker pull localhost:5000/testimage:testtag").waitFor();
      Process process = Runtime.getRuntime().exec("docker run localhost:5000/testimage:testtag");
      try (InputStreamReader inputStreamReader =
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
        String output = CharStreams.toString(inputStreamReader);
        Assert.assertEquals("Hello world\n", output);
      }
      process.waitFor();

      // TODO: Integrate any new steps as they are added.
    }
  }

  private List<String> getEntrypoint(
      SourceFilesConfiguration sourceFilesConfiguration, String mainClass) {
    List<String> classPaths = new ArrayList<>();
    addSourceFilesToClassPaths(
        sourceFilesConfiguration.getDependenciesFiles(),
        sourceFilesConfiguration.getDependenciesPathOnImage(),
        classPaths);
    addSourceFilesToClassPaths(
        sourceFilesConfiguration.getResourcesFiles(),
        sourceFilesConfiguration.getResourcesPathOnImage(),
        classPaths);
    addSourceFilesToClassPaths(
        sourceFilesConfiguration.getClassesFiles(),
        sourceFilesConfiguration.getClassesPathOnImage(),
        classPaths);

    String entrypoint = String.join(":", classPaths);

    return Arrays.asList("java", "-cp", entrypoint, mainClass);
  }

  private void addSourceFilesToClassPaths(
      List<Path> sourceFiles, Path extractionPath, List<String> classPaths) {
    sourceFiles.forEach(
        sourceFile -> {
          Path containerPath = extractionPath;
          if (!Files.isDirectory(sourceFile)) {
            containerPath = containerPath.resolve(sourceFile.getFileName());
          }
          classPaths.add(containerPath.toString());
        });
  }
}
