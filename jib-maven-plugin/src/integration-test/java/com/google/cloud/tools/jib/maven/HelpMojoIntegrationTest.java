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

package com.google.cloud.tools.jib.maven;

import java.io.IOException;
import java.security.DigestException;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link HelpMojo}. */
public class HelpMojoIntegrationTest {

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @ClassRule public static final TestProject emptyTestProject = new TestProject("empty");

  @ClassRule
  public static final TestProject defaultTargetTestProject = new TestProject("default-target");

  @Test
  public void testExecute_simple()
      throws VerificationException, IOException, InterruptedException, DigestException {
    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setAutoclean(false);

    verifier.executeGoal("jib:help");
    verifier.verifyErrorFreeLog();

    verifier.verifyTextInLog(
        "A Maven plugin for building container images for your Java applications.");
    verifier.verifyTextInLog("This plugin has 9 goals:");
    verifier.verifyTextInLog(":_skaffold-fail-if-jib-out-of-date");
    verifier.verifyTextInLog(":_skaffold-files-v2");
    verifier.verifyTextInLog(":_skaffold-init");
    verifier.verifyTextInLog(":_skaffold-package-goals");
    verifier.verifyTextInLog(":_skaffold-sync-map");
    verifier.verifyTextInLog(":build");
    verifier.verifyTextInLog(":buildTar");
    verifier.verifyTextInLog(":dockerBuild");
    verifier.verifyTextInLog(":help");
  }

  @Test
  public void testExecute_empty()
      throws VerificationException, IOException, InterruptedException, DigestException {
    Verifier verifier = new Verifier(emptyTestProject.getProjectRoot().toString());
    verifier.setSystemProperty("jib.useOnlyProjectCache", "true");
    verifier.setAutoclean(false);

    verifier.executeGoal("jib:help");
    verifier.verifyErrorFreeLog();

    verifier.verifyTextInLog(
        "A Maven plugin for building container images for your Java applications.");
    verifier.verifyTextInLog("This plugin has 9 goals:");
    verifier.verifyTextInLog(":_skaffold-fail-if-jib-out-of-date");
    verifier.verifyTextInLog(":_skaffold-files-v2");
    verifier.verifyTextInLog(":_skaffold-init");
    verifier.verifyTextInLog(":_skaffold-package-goals");
    verifier.verifyTextInLog(":_skaffold-sync-map");
    verifier.verifyTextInLog(":build");
    verifier.verifyTextInLog(":buildTar");
    verifier.verifyTextInLog(":dockerBuild");
    verifier.verifyTextInLog(":help");
  }
}
