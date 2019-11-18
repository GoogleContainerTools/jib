/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.maven.skaffold;

import com.google.cloud.tools.jib.maven.MojoCommon;
import com.google.cloud.tools.jib.maven.TestProject;
import java.io.IOException;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for {@link CheckJibVersionMojo}. */
public class CheckJibVersionMojoTest {

  @ClassRule public static final TestProject simpleTestProject = new TestProject("simple");

  @Test
  public void testIdentifiers() {
    // These identifiers will be baked into Skaffold and should not be changed
    Assert.assertEquals("_skaffold-fail-if-jib-out-of-date", CheckJibVersionMojo.GOAL_NAME);
    Assert.assertEquals("jib.requiredVersion", MojoCommon.REQUIRED_VERSION_PROPERTY_NAME);
  }

  @Test
  public void testFailOnMissingProperty() throws VerificationException, IOException {
    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    try {
      verifier.executeGoal("jib:" + CheckJibVersionMojo.GOAL_NAME);
      Assert.fail("build should have failed");
    } catch (VerificationException ex) {
      verifier.verifyTextInLog("requires jib.requiredVersion to be set");
    }
  }

  @Test
  public void testFailOnOutOfDate() throws VerificationException, IOException {
    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setSystemProperty(MojoCommon.REQUIRED_VERSION_PROPERTY_NAME, "[,1.0)");
    try {
      verifier.executeGoal("jib:" + CheckJibVersionMojo.GOAL_NAME);
      Assert.fail("build should have failed");
    } catch (VerificationException ex) {
      verifier.verifyTextInLog("but is required to be [,1.0)");
    }
  }

  @Test
  public void testSuccess() throws VerificationException, IOException {
    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setSystemProperty(MojoCommon.REQUIRED_VERSION_PROPERTY_NAME, "[1.0,)");
    verifier.executeGoal("jib:" + CheckJibVersionMojo.GOAL_NAME);
    verifier.verifyErrorFreeLog();
  }
}
