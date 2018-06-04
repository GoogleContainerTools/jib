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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import java.util.Arrays;
import java.util.Collections;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link JibExtension}. */
public class JibExtensionTest {

  private JibExtension testJibExtension;

  @Before
  public void setUp() {
    Project fakeProject = ProjectBuilder.builder().build();
    testJibExtension = fakeProject.getExtensions().create("jib", JibExtension.class, fakeProject);
  }

  @Test
  public void testFrom() {
    Assert.assertEquals("gcr.io/distroless/java", testJibExtension.getFrom().getImage());
    Assert.assertNull(testJibExtension.getFrom().getCredHelper());

    testJibExtension.from(
        from -> {
          from.setImage("some image");
          from.setCredHelper("some cred helper");
          from.auth(
              auth -> {
                auth.setUsername("some username");
                auth.setPassword("some password");
              });
        });
    Assert.assertEquals("some image", testJibExtension.getFrom().getImage());
    Assert.assertEquals("some cred helper", testJibExtension.getFrom().getCredHelper());
    Assert.assertEquals("some username", testJibExtension.getFrom().getAuth().getUsername());
    Assert.assertEquals("some password", testJibExtension.getFrom().getAuth().getPassword());
  }

  @Test
  public void testTo() {
    Assert.assertNull(testJibExtension.getTo().getImage());
    Assert.assertNull(testJibExtension.getTo().getCredHelper());

    testJibExtension.to(
        to -> {
          to.setImage("some image");
          to.setCredHelper("some cred helper");
          to.auth(
              auth -> {
                auth.setUsername("some username");
                auth.setPassword("some password");
              });
        });
    Assert.assertEquals("some image", testJibExtension.getTo().getImage());
    Assert.assertEquals("some cred helper", testJibExtension.getTo().getCredHelper());
    Assert.assertEquals("some username", testJibExtension.getTo().getAuth().getUsername());
    Assert.assertEquals("some password", testJibExtension.getTo().getAuth().getPassword());
  }

  @Test
  public void testJvmFlags() {
    Assert.assertEquals(Collections.emptyList(), testJibExtension.getJvmFlags());

    testJibExtension.setJvmFlags(Arrays.asList("flag1", "flag2"));
    Assert.assertEquals(Arrays.asList("flag1", "flag2"), testJibExtension.getJvmFlags());
  }

  @Test
  public void testMainClass() {
    Assert.assertNull(testJibExtension.getMainClass());

    testJibExtension.setMainClass("some main class");
    Assert.assertEquals("some main class", testJibExtension.getMainClass());
  }

  @Test
  public void testArgs() {
    Assert.assertEquals(Collections.emptyList(), testJibExtension.getArgs());

    testJibExtension.setArgs(Arrays.asList("flag1", "flag2"));
    Assert.assertEquals(Arrays.asList("flag1", "flag2"), testJibExtension.getArgs());
  }

  @Test
  public void testFormat() {
    Assert.assertEquals(V22ManifestTemplate.class, testJibExtension.getFormat());

    testJibExtension.setFormat(JibExtension.ImageFormat.OCI);
    Assert.assertEquals(OCIManifestTemplate.class, testJibExtension.getFormat());
  }

  @Test
  public void testUseOnlyProjectCache() {
    Assert.assertFalse(testJibExtension.getUseOnlyProjectCache());

    testJibExtension.setUseOnlyProjectCache(true);
    Assert.assertTrue(testJibExtension.getUseOnlyProjectCache());
  }
}
