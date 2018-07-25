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

import com.google.cloud.tools.jib.Command;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Integration tests for {@link DockerContextMojo}. */
@RunWith(MockitoJUnitRunner.class)
public class DockerContextMojoIntegrationTest {

  @ClassRule public static final TestPlugin testPlugin = new TestPlugin();

  @ClassRule
  public static final TestProject simpleTestProject = new TestProject(testPlugin, "simple");

  @Test
  public void testExecute() throws VerificationException, IOException, InterruptedException {
    Verifier verifier = new Verifier(simpleTestProject.getProjectRoot().toString());
    verifier.setAutoclean(false);
    verifier.executeGoal("compile");
    verifier.executeGoal("jib:" + DockerContextMojo.GOAL_NAME);

    Path dockerContextDirectory =
        simpleTestProject.getProjectRoot().resolve("target").resolve("jib-docker-context");
    Assert.assertTrue(Files.exists(dockerContextDirectory));

    String imageName = "jib/integration-test";
    new Command("docker", "build", "-t", imageName, dockerContextDirectory.toString()).run();
    Assert.assertThat(
        new Command("docker", "inspect", imageName).run(),
        CoreMatchers.containsString(
            "            \"ExposedPorts\": {\n"
                + "                \"1000/tcp\": {},\n"
                + "                \"2000/udp\": {},\n"
                + "                \"2001/udp\": {},\n"
                + "                \"2002/udp\": {},\n"
                + "                \"2003/udp\": {}"));

    Assert.assertEquals(
        "Hello, world. An argument.\nfoo\ncat\n", new Command("docker", "run", imageName).run());
  }
}
