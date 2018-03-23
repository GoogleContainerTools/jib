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

import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import java.util.Arrays;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link BuildImageTask}. */
public class BuildImageTaskTest {

  private BuildImageTask testBuildImageTask;
  private JibExtension fakeJibExtension;

  @Before
  public void setUp() {
    Project fakeProject = ProjectBuilder.builder().build();
    testBuildImageTask = fakeProject.getTasks().create("task", BuildImageTask.class);
    fakeJibExtension = fakeProject.getExtensions().create("jib", JibExtension.class, fakeProject);
  }

  @Test
  public void testSetExtension() {
    fakeJibExtension.from(
        from -> {
          from.setImage("some image");
          from.setCredHelper("some cred helper");
        });
    fakeJibExtension.to(
        to -> {
          to.setImage("another image");
          to.setCredHelper("another cred helper");
        });
    fakeJibExtension.setJvmFlags(Arrays.asList("flag1", "flag2"));
    fakeJibExtension.setMainClass("some main class");
    fakeJibExtension.setReproducible(false);
    fakeJibExtension.setFormat(JibExtension.ImageFormat.Docker);

    testBuildImageTask.applyExtension(fakeJibExtension);

    Assert.assertNotNull(testBuildImageTask.getFrom());
    Assert.assertEquals("some image", testBuildImageTask.getFrom().getImage());
    Assert.assertEquals("some cred helper", testBuildImageTask.getFrom().getCredHelper());
    Assert.assertNotNull(testBuildImageTask.getTo());
    Assert.assertEquals("another image", testBuildImageTask.getTo().getImage());
    Assert.assertEquals("another cred helper", testBuildImageTask.getTo().getCredHelper());
    Assert.assertEquals(Arrays.asList("flag1", "flag2"), testBuildImageTask.getJvmFlags());
    Assert.assertEquals("some main class", testBuildImageTask.getMainClass());
    Assert.assertEquals(false, testBuildImageTask.getReproducible());
    Assert.assertEquals(V22ManifestTemplate.class, testBuildImageTask.getFormat());
  }
}
