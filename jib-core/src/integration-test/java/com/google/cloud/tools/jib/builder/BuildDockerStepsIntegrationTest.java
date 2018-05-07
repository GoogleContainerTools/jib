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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Integration tests for {@link BuildImageSteps}. */
public class BuildDockerStepsIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  private static final TestBuildLogger logger = new TestBuildLogger();

  @Rule public TemporaryFolder temporaryCacheDirectory = new TemporaryFolder();

  @Test
  public void testSteps() throws Exception {
    SourceFilesConfiguration sourceFilesConfiguration = new TestSourceFilesConfiguration();
    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(logger)
            .setBaseImage(ImageReference.of("gcr.io", "distroless/java", "latest"))
            .setTargetImage(ImageReference.of(null, "testdocker", null))
            .setMainClass("HelloWorld")
            .build();

    Path cacheDirectory = temporaryCacheDirectory.newFolder().toPath();
    BuildDockerSteps buildDockerSteps =
        new BuildDockerSteps(
            buildConfiguration,
            sourceFilesConfiguration,
            Caches.newInitializer(cacheDirectory).setBaseCacheDirectory(cacheDirectory));

    long lastTime = System.nanoTime();
    buildDockerSteps.run();
    logger.info("Initial build time: " + ((System.nanoTime() - lastTime) / 1_000_000));
    lastTime = System.nanoTime();
    buildDockerSteps.run();
    logger.info("Secondary build time: " + ((System.nanoTime() - lastTime) / 1_000_000));

    Assert.assertEquals("Hello world\n", new Command("docker", "run", "testdocker").run());
  }
}
