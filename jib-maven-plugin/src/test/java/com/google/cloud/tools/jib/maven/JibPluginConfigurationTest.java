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

import com.google.cloud.tools.jib.maven.JibPluginConfiguration.PermissionConfiguration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link JibPluginConfiguration}. */
public class JibPluginConfigurationTest {

  private JibPluginConfiguration testPluginConfiguration;

  private static void clearProperties() {
    System.clearProperty("jib.from.image");
    System.clearProperty("jib.from.credHelper");
    System.clearProperty("image");
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
    System.clearProperty("jib.container.workingDirectory");
    System.clearProperty("jib.extraDirectory.path");
    System.clearProperty("jib.extraDirectory.permissions");
  }

  @Before
  public void setup() {
    clearProperties();
    testPluginConfiguration =
        new JibPluginConfiguration() {
          @Override
          public void execute() {}
        };
  }

  @After
  public void teardown() {
    clearProperties();
  }

  @Test
  public void testDefaults() {
    Assert.assertEquals("", testPluginConfiguration.getAppRoot());
    Assert.assertNull(testPluginConfiguration.getWorkingDirectory());
  }

  @Test
  public void testSystemProperties() {
    System.setProperty("jib.from.image", "fromImage");
    Assert.assertEquals("fromImage", testPluginConfiguration.getBaseImage());
    System.setProperty("jib.from.credHelper", "credHelper");
    Assert.assertEquals("credHelper", testPluginConfiguration.getBaseImageCredentialHelperName());

    System.setProperty("image", "toImage");
    Assert.assertEquals("toImage", testPluginConfiguration.getTargetImage());
    System.clearProperty("image");
    System.setProperty("jib.to.image", "toImage2");
    Assert.assertEquals("toImage2", testPluginConfiguration.getTargetImage());
    System.setProperty("jib.to.tags", "tag1,tag2,tag3");
    Assert.assertEquals(
        ImmutableSet.of("tag1", "tag2", "tag3"),
        testPluginConfiguration.getTargetImageAdditionalTags());
    System.setProperty("jib.to.credHelper", "credHelper");
    Assert.assertEquals("credHelper", testPluginConfiguration.getTargetImageCredentialHelperName());

    System.setProperty("jib.container.appRoot", "appRoot");
    Assert.assertEquals("appRoot", testPluginConfiguration.getAppRoot());
    System.setProperty("jib.container.args", "arg1,arg2,arg3");
    Assert.assertEquals(
        ImmutableList.of("arg1", "arg2", "arg3"), testPluginConfiguration.getArgs());
    System.setProperty("jib.container.entrypoint", "entry1,entry2,entry3");
    Assert.assertEquals(
        ImmutableList.of("entry1", "entry2", "entry3"), testPluginConfiguration.getEntrypoint());
    System.setProperty("jib.container.environment", "env1=val1,env2=val2");
    Assert.assertEquals(
        ImmutableMap.of("env1", "val1", "env2", "val2"), testPluginConfiguration.getEnvironment());
    System.setProperty("jib.container.format", "format");
    Assert.assertEquals("format", testPluginConfiguration.getFormat());
    System.setProperty("jib.container.jvmFlags", "flag1,flag2,flag3");
    Assert.assertEquals(
        ImmutableList.of("flag1", "flag2", "flag3"), testPluginConfiguration.getJvmFlags());
    System.setProperty("jib.container.labels", "label1=val1,label2=val2");
    Assert.assertEquals(
        ImmutableMap.of("label1", "val1", "label2", "val2"), testPluginConfiguration.getLabels());
    System.setProperty("jib.container.mainClass", "main");
    Assert.assertEquals("main", testPluginConfiguration.getMainClass());
    System.setProperty("jib.container.ports", "port1,port2,port3");
    Assert.assertEquals(
        ImmutableList.of("port1", "port2", "port3"), testPluginConfiguration.getExposedPorts());
    System.setProperty("jib.container.useCurrentTimestamp", "true");
    Assert.assertTrue(testPluginConfiguration.getUseCurrentTimestamp());
    System.setProperty("jib.container.user", "myUser");
    Assert.assertEquals("myUser", testPluginConfiguration.getUser());
    System.setProperty("jib.container.workingDirectory", "working directory");
    Assert.assertEquals("working directory", testPluginConfiguration.getWorkingDirectory());

    System.setProperty("jib.extraDirectory.path", "custom-jib");
    Assert.assertEquals(
        Paths.get("custom-jib"), testPluginConfiguration.getExtraDirectoryPath().get());
    System.setProperty("jib.extraDirectory.permissions", "/test/file1=123,/another/file=456");
    List<PermissionConfiguration> permissions =
        testPluginConfiguration.getExtraDirectoryPermissions();
    Assert.assertEquals("/test/file1", permissions.get(0).getFile().get());
    Assert.assertEquals("123", permissions.get(0).getMode().get());
    Assert.assertEquals("/another/file", permissions.get(1).getFile().get());
    Assert.assertEquals("456", permissions.get(1).getMode().get());
  }
}
