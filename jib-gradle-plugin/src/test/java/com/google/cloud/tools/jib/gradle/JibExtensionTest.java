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

import com.google.cloud.tools.jib.image.ImageFormat;
import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.common.collect.ImmutableMap;
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
    testJibExtension =
        fakeProject
            .getExtensions()
            .create(JibPlugin.JIB_EXTENSION_NAME, JibExtension.class, fakeProject);
  }

  @Test
  public void testFrom() {
    Assert.assertEquals(null, testJibExtension.getFrom().getImage());
    Assert.assertNull(testJibExtension.getFrom().getCredHelper());

    testJibExtension.from(
        from -> {
          from.setImage("some image");
          from.setCredHelper("some cred helper");
          from.auth(auth -> auth.setUsername("some username"));
          from.auth(auth -> auth.setPassword("some password"));
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
          to.auth(auth -> auth.setUsername("some username"));
          to.auth(auth -> auth.setPassword("some password"));
        });
    Assert.assertEquals("some image", testJibExtension.getTo().getImage());
    Assert.assertEquals("some cred helper", testJibExtension.getTo().getCredHelper());
    Assert.assertEquals("some username", testJibExtension.getTo().getAuth().getUsername());
    Assert.assertEquals("some password", testJibExtension.getTo().getAuth().getPassword());
  }

  @Test
  public void testContainer() {
    Assert.assertEquals(Collections.emptyList(), testJibExtension.getContainer().getJvmFlags());
    Assert.assertEquals(Collections.emptyMap(), testJibExtension.getContainer().getEnvironment());
    Assert.assertNull(testJibExtension.getContainer().getMainClass());
    Assert.assertEquals(Collections.emptyList(), testJibExtension.getContainer().getArgs());
    Assert.assertEquals(V22ManifestTemplate.class, testJibExtension.getContainer().getFormat());
    Assert.assertEquals(Collections.emptyList(), testJibExtension.getContainer().getPorts());
    Assert.assertEquals(Collections.emptyMap(), testJibExtension.getContainer().getLabels());
    Assert.assertEquals("", testJibExtension.getContainer().getAppRoot());

    testJibExtension.container(
        container -> {
          container.setJvmFlags(Arrays.asList("jvmFlag1", "jvmFlag2"));
          container.setEnvironment(ImmutableMap.of("var1", "value1", "var2", "value2"));
          container.setEntrypoint(Arrays.asList("foo", "bar", "baz"));
          container.setMainClass("mainClass");
          container.setArgs(Arrays.asList("arg1", "arg2", "arg3"));
          container.setPorts(Arrays.asList("1000", "2000-2010", "3000"));
          container.setLabels(ImmutableMap.of("label1", "value1", "label2", "value2"));
          container.setFormat(ImageFormat.OCI);
          container.setAppRoot("some invalid appRoot value");
        });
    ContainerParameters container = testJibExtension.getContainer();
    Assert.assertEquals(Arrays.asList("foo", "bar", "baz"), container.getEntrypoint());
    Assert.assertEquals(Arrays.asList("jvmFlag1", "jvmFlag2"), container.getJvmFlags());
    Assert.assertEquals(
        ImmutableMap.of("var1", "value1", "var2", "value2"), container.getEnvironment());
    Assert.assertEquals("mainClass", testJibExtension.getContainer().getMainClass());
    Assert.assertEquals(Arrays.asList("arg1", "arg2", "arg3"), container.getArgs());
    Assert.assertEquals(Arrays.asList("1000", "2000-2010", "3000"), container.getPorts());
    Assert.assertEquals(
        ImmutableMap.of("label1", "value1", "label2", "value2"), container.getLabels());
    Assert.assertEquals(OCIManifestTemplate.class, container.getFormat());
    Assert.assertEquals("some invalid appRoot value", container.getAppRoot());
  }

  @Test
  public void testUseOnlyProjectCache() {
    Assert.assertFalse(testJibExtension.getUseOnlyProjectCache());

    testJibExtension.setUseOnlyProjectCache(true);
    Assert.assertTrue(testJibExtension.getUseOnlyProjectCache());
  }
}
