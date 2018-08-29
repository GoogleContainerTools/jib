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

package com.google.cloud.tools.jib.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

public class EntrypointIntegrationTest {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject entrypointTestProject = new TestProject(testPlugin, "entrypoint");

  /**
   * Builds and runs jib:buildTar on a project at {@code projectRoot} and examines the resulting
   * configuration.
   */
  @Test
  public void testExecute() throws VerificationException, IOException {
    String targetImage = "entrypoint:maven" + System.nanoTime();

    Verifier verifier = new Verifier(entrypointTestProject.getProjectRoot().toString());
    verifier.setSystemProperty("_TARGET_IMAGE", targetImage);
    verifier.setAutoclean(false);
    verifier.executeGoal("package");

    verifier.executeGoal("jib:" + BuildTarMojo.GOAL_NAME);
    verifier.verifyErrorFreeLog();

    Path tarFile =
        entrypointTestProject.getProjectRoot().resolve("target").resolve("jib-image.tar");
    try (InputStream configuration = extractTarContent(tarFile, "config.json")) {
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode node = objectMapper.readTree(configuration);
      JsonNode entrypoint = node.get("config").get("Entrypoint");
      Assert.assertNotNull(entrypoint);
      Assert.assertTrue(entrypoint.isArray());
      Assert.assertEquals(2, entrypoint.size());
      Assert.assertTrue(entrypoint.get(0).isTextual());
      Assert.assertEquals("custom", entrypoint.get(0).asText());
      Assert.assertTrue(entrypoint.get(1).isTextual());
      Assert.assertEquals("entrypoint", entrypoint.get(1).asText());
    }
  }

  private static InputStream extractTarContent(Path tarFile, String filename) throws IOException {
    TarArchiveInputStream tarInput = new TarArchiveInputStream(Files.newInputStream(tarFile));
    TarArchiveEntry entry;
    while ((entry = tarInput.getNextTarEntry()) != null) {
      if (filename.equals(entry.getName())) {
        return tarInput;
      }
    }
    throw new FileNotFoundException(filename + " not found in " + tarFile);
  }
}
