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

package com.google.cloud.tools.jib.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** Tests for {@link BuildImageMojo}. */
public class BuildImageMojoTest {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @Rule public final TestProject testProject = new TestProject(testPlugin);

  private static Logger log = Logger.getLogger("info");

  @Test
  public void testExecute() throws VerificationException, IOException {
    Verifier verifier = new Verifier(testProject.getProjectRoot().toString());
    verifier.setAutoclean(false);
    verifier.executeGoal("package");

    long lastTime = System.nanoTime();
    verifier.executeGoal("jib:build");
    long timeOne = (System.nanoTime() - lastTime) / 1_000_000;
    lastTime = System.nanoTime();

    verifier.executeGoal("jib:build");
    long timeTwo = (System.nanoTime() - lastTime) / 1_000_000;

    verifier.verifyErrorFreeLog();

    System.out.println(Paths.get(verifier.getLogFileName()));
    log.info("I'm starting");
    System.out.println(
        new String(
            Files.readAllBytes(Paths.get(verifier.getLogFileName())), StandardCharsets.UTF_8));
    Assert.fail(timeOne + " > " + timeTwo);
  }
}
