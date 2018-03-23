/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

package com.google.cloud.tools.jib.gradle;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

/** Integration tests for {@link JibPlugin}. */
public class JibPluginIntegrationTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Before
  public void setUp() throws IOException {
    Project testProject = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
    testProject.getPluginManager().apply(JibPlugin.class);
    JibExtension ex = (JibExtension) testProject.getExtensions().getByName("jib");
    ((ProjectInternal) testProject).evaluate();

    testProject.
  }

  @Test
  public void testExecute_simple() {
    Assert.assertEquals("Hello, world\n", buildAndRun())
  }
}
