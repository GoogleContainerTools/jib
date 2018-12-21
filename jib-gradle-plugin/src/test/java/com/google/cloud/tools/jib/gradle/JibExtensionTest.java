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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link JibExtension}. */
public class JibExtensionTest {

  private JibExtension testJibExtension;
  private Project fakeProject;

  private static void clearProperties() {
    System.clearProperty("jib.from.image");
    System.clearProperty("jib.from.credHelper");
    System.clearProperty("jib.to.image");
    System.clearProperty("jib.to.tags");
    System.clearProperty("jib.to.credHelper");
    System.clearProperty("jib.container.appRoot");
    System.clearProperty("jib.container.args");
    System.clearProperty("jib.container.entrypoint");
    System.clearProperty("jib.container.environment");
    System.clearProperty("jib.container.format");
    System.clearProperty("jib.container.jvmFlags");
    System.clearProperty("jib.container.labels");
    System.clearProperty("jib.container.mainClass");
    System.clearProperty("jib.container.ports");
    System.clearProperty("jib.container.useCurrentTimestamp");
    System.clearProperty("jib.container.user");
    System.clearProperty("jib.extraDirectory.path");
    System.clearProperty("jib.extraDirectory.permissions");
    System.clearProperty("jib.packagingOverride");
  }

  @Before
  public void setUp() {
    clearProperties();
    fakeProject = ProjectBuilder.builder().build();
    testJibExtension =
        fakeProject
            .getExtensions()
            .create(JibPlugin.JIB_EXTENSION_NAME, JibExtension.class, fakeProject);
  }

  @After
  public void teardown() {
    clearProperties();
  }

  @Test
  public void testFrom() {
    Assert.assertNull(testJibExtension.getFrom().getImage());
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
    Assert.assertNull(testJibExtension.getContainer().getArgs());
    Assert.assertSame(ImageFormat.Docker, testJibExtension.getContainer().getFormat());
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
    Assert.assertSame(ImageFormat.OCI, container.getFormat());
    Assert.assertEquals("some invalid appRoot value", container.getAppRoot());
  }

  @Test
  public void testExtraDirectory() {
    Assert.assertEquals(
        fakeProject.getProjectDir().toPath().resolve("src").resolve("main").resolve("jib"),
        testJibExtension.getExtraDirectory().getPath());
    Assert.assertEquals(
        Collections.emptyMap(), testJibExtension.getExtraDirectory().getPermissions());

    testJibExtension.extraDirectory(
        extraDirectory -> {
          extraDirectory.setPath(Paths.get("test").resolve("path").toFile());
          extraDirectory.setPermissions(ImmutableMap.of("file1", "123", "file2", "456"));
        });
    Assert.assertEquals(
        Paths.get("test").resolve("path"), testJibExtension.getExtraDirectory().getPath());
    Assert.assertEquals(
        ImmutableMap.of("file1", "123", "file2", "456"),
        testJibExtension.getExtraDirectory().getPermissions());
  }

  @Test
  public void testPackagingOverride() {
    testJibExtension.setPackagingOverride("do this way");
    Assert.assertEquals("do this way", testJibExtension.getPackagingOverride());
  }

  @Test
  public void testProperties() {
    System.setProperty("jib.from.image", "fromImage");
    Assert.assertEquals("fromImage", testJibExtension.getFrom().getImage());
    System.setProperty("jib.from.credHelper", "credHelper");
    Assert.assertEquals("credHelper", testJibExtension.getFrom().getCredHelper());

    System.setProperty("jib.to.image", "toImage");
    Assert.assertEquals("toImage", testJibExtension.getTo().getImage());
    System.setProperty("jib.to.tags", "tag1,tag2,tag3");
    Assert.assertEquals(
        ImmutableSet.of("tag1", "tag2", "tag3"), testJibExtension.getTo().getTags());
    System.setProperty("jib.to.credHelper", "credHelper");
    Assert.assertEquals("credHelper", testJibExtension.getTo().getCredHelper());

    System.setProperty("jib.container.appRoot", "appRoot");
    Assert.assertEquals("appRoot", testJibExtension.getContainer().getAppRoot());
    System.setProperty("jib.container.args", "arg1,arg2,arg3");
    Assert.assertEquals(
        ImmutableList.of("arg1", "arg2", "arg3"), testJibExtension.getContainer().getArgs());
    System.setProperty("jib.container.entrypoint", "entry1,entry2,entry3");
    Assert.assertEquals(
        ImmutableList.of("entry1", "entry2", "entry3"),
        testJibExtension.getContainer().getEntrypoint());
    System.setProperty("jib.container.environment", "env1=val1,env2=val2");
    Assert.assertEquals(
        ImmutableMap.of("env1", "val1", "env2", "val2"),
        testJibExtension.getContainer().getEnvironment());
    System.setProperty("jib.container.format", "OCI");
    Assert.assertSame(ImageFormat.OCI, testJibExtension.getContainer().getFormat());
    System.setProperty("jib.container.jvmFlags", "flag1,flag2,flag3");
    Assert.assertEquals(
        ImmutableList.of("flag1", "flag2", "flag3"), testJibExtension.getContainer().getJvmFlags());
    System.setProperty("jib.container.labels", "label1=val1,label2=val2");
    Assert.assertEquals(
        ImmutableMap.of("label1", "val1", "label2", "val2"),
        testJibExtension.getContainer().getLabels());
    System.setProperty("jib.container.mainClass", "main");
    Assert.assertEquals("main", testJibExtension.getContainer().getMainClass());
    System.setProperty("jib.container.ports", "port1,port2,port3");
    Assert.assertEquals(
        ImmutableList.of("port1", "port2", "port3"), testJibExtension.getContainer().getPorts());
    System.setProperty("jib.container.useCurrentTimestamp", "true");
    Assert.assertTrue(testJibExtension.getContainer().getUseCurrentTimestamp());
    System.setProperty("jib.container.user", "myUser");
    Assert.assertEquals("myUser", testJibExtension.getContainer().getUser());
    System.setProperty("jib.packagingOverride", "do this way");
    Assert.assertEquals("do this way", testJibExtension.getPackagingOverride());
  }
}
