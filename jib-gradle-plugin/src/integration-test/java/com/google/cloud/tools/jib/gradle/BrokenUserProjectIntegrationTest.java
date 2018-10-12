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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.Command;
import java.io.IOException;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for building "broken-user" project images. */
public class BrokenUserProjectIntegrationTest {

  @ClassRule public static final TestProject brokenUserTestProject = new TestProject("broken_user");

  @Test
  public void testDockerDaemon_userNames() throws IOException, InterruptedException {
    String targetImage = "brokenuserimage:gradle" + System.nanoTime();
    JibRunHelper.buildToDockerDaemon(brokenUserTestProject, targetImage);
    Assert.assertEquals(
        "myuser:mygroup",
        new Command("docker", "inspect", "-f", "{{.Config.User}}", targetImage).run().trim());
  }
}
