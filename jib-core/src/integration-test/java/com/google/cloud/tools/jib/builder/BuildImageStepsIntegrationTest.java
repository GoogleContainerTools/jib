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

import com.google.cloud.tools.jib.registry.LocalRegistry;
import com.google.common.io.CharStreams;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Integration tests for {@link BuildImageSteps}. */
public class BuildImageStepsIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  private static final Logger logger = LoggerFactory.getLogger(TestBuildLogger.class);

  @Rule public TemporaryFolder temporaryCacheDirectory = new TemporaryFolder();

  @Test
  public void testSteps() throws Exception {
    SourceFilesConfiguration sourceFilesConfiguration = new TestSourceFilesConfiguration();
    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder()
            .setBaseImageServerUrl("gcr.io")
            .setBaseImageName("distroless/java")
            .setBaseImageTag("latest")
            .setTargetServerUrl("localhost:5000")
            .setTargetImageName("testimage")
            .setTargetTag("testtag")
            .setMainClass("HelloWorld")
            .setBuildLogger(new TestBuildLogger())
            .build();

    BuildImageSteps buildImageSteps =
        new BuildImageSteps(
            buildConfiguration,
            sourceFilesConfiguration,
            temporaryCacheDirectory.getRoot().toPath());

    long lastTime = System.nanoTime();
    buildImageSteps.runAsync();
    logger.info("Initial build time: " + ((System.nanoTime() - lastTime) / 1_000_000));
    lastTime = System.nanoTime();
    buildImageSteps.runAsync();
    logger.info("Secondary build time: " + ((System.nanoTime() - lastTime) / 1_000_000));

    // TODO: Put this in a utility function.
    Runtime.getRuntime().exec("docker pull localhost:5000/testimage:testtag").waitFor();
    Process process = Runtime.getRuntime().exec("docker run localhost:5000/testimage:testtag");
    try (InputStreamReader inputStreamReader =
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
      String output = CharStreams.toString(inputStreamReader);
      Assert.assertEquals("Hello world\n", output);
    }
    process.waitFor();
  }
}
