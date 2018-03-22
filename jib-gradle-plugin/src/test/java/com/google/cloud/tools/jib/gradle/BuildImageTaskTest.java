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
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;

/** Tests for {@link BuildImageTask}. */
public class BuildImageTaskTest {

  private BuildImageTask testBuildImageTask;

  @Before
  public void setUp() {
    Project fakeProject = ProjectBuilder.builder().build();
    testBuildImageTask = fakeProject.getTasks().create("task", BuildImageTask.class);
  }

  //  @Test
  //  public void testConfigureFrom() {
  //    testBuildImageTask.(
  //        getClosureWithProperties(
  //            ImmutableMap.of("image", "some image", "credHelper", "some credential helper")));
  //
  //    Assert.assertEquals("some image", testBuildImageTask.getFrom().getImage());
  //    Assert.assertEquals("some credential helper", testBuildImageTask.getFrom().getCredHelper());
  //  }
  //
  //  @Test
  //  public void testConfigureFrom_nonexistentProperty() {
  //    try {
  //      testBuildImageTask.from(
  //          getClosureWithProperties(ImmutableMap.of("nonexistent property", "invalid")));
  //      Assert.fail("Should not be able to configure with nonexistent property");
  //
  //    } catch (MissingPropertyException ex) {
  //      // pass
  //    }
  //  }
  //
  //  @Test
  //  public void testConfigureTo() {
  //    testBuildImageTask.to(
  //        getClosureWithProperties(
  //            ImmutableMap.of("image", "another image", "credHelper", "another credential
  // helper")));
  //
  //    Assert.assertEquals("another image", testBuildImageTask.getTo().getImage());
  //    Assert.assertEquals("another credential helper",
  // testBuildImageTask.getTo().getCredHelper());
  //  }
  //
  //  /**
  //   * Generates a closure with the {@code properties} set.
  //   *
  //   * @param properties maps from property name to value
  //   */
  //  private Closure<Void> getClosureWithProperties(Map<String, String> properties) {
  //    return new Closure<Void>(this) {
  //
  //      public void doCall() {
  //        for (Map.Entry<String, String> property : properties.entrySet()) {
  //          setProperty(property.getKey(), property.getValue());
  //        }
  //      }
  //    };
  //  }
}
