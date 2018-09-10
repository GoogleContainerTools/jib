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

import com.google.cloud.tools.jib.JibLogger;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link JibPluginConfiguration}. */
@RunWith(MockitoJUnitRunner.class)
public class JibPluginConfigurationTest {

  @Mock private JibLogger mockLogger;

  private JibPluginConfiguration testPluginConfiguration;

  @Before
  public void setup() {
    testPluginConfiguration =
        new JibPluginConfiguration() {
          @Override
          public void execute() {}
        };
  }

  @Test
  public void testAuthDefaults() {
    Assert.assertEquals(
        "<from><auth><username>",
        testPluginConfiguration.getBaseImageAuth().getUsernamePropertyDescriptor());
    Assert.assertEquals(
        "<from><auth><password>",
        testPluginConfiguration.getBaseImageAuth().getPasswordPropertyDescriptor());
    Assert.assertEquals(
        "<to><auth><username>",
        testPluginConfiguration.getTargetImageAuth().getUsernamePropertyDescriptor());
    Assert.assertEquals(
        "<to><auth><password>",
        testPluginConfiguration.getTargetImageAuth().getPasswordPropertyDescriptor());
  }

  @Test
  public void testHandleDeprecatedParameters() {
    testPluginConfiguration.handleDeprecatedParameters(mockLogger);
    Mockito.verify(mockLogger, Mockito.never()).warn(Mockito.any());

    testPluginConfiguration.setJvmFlags(Arrays.asList("jvmFlag1", "jvmFlag2"));
    testPluginConfiguration.setMainClass("mainClass");
    testPluginConfiguration.setArgs(Arrays.asList("arg1", "arg2", "arg3"));
    testPluginConfiguration.setFormat("OCI");
    testPluginConfiguration.setExtraDirectory(new File("some/path"));

    testPluginConfiguration.handleDeprecatedParameters(mockLogger);

    String expectedOutput =
        "There are deprecated parameters used in the build configuration. Please make the "
            + "following changes to your pom.xml to avoid issues in the future:\n"
            + "  <jvmFlags> -> <container><jvmFlags>\n"
            + "  <mainClass> -> <container><mainClass>\n"
            + "  <args> -> <container><args>\n"
            + "  <format> -> <container><format>\n";
    Mockito.verify(mockLogger).warn(expectedOutput);
    Assert.assertEquals(
        Arrays.asList("jvmFlag1", "jvmFlag2"), testPluginConfiguration.getJvmFlags());
    Assert.assertEquals("mainClass", testPluginConfiguration.getMainClass());
    Assert.assertEquals(Arrays.asList("arg1", "arg2", "arg3"), testPluginConfiguration.getArgs());
    Assert.assertEquals("OCI", testPluginConfiguration.getFormat());
    Assert.assertEquals(Paths.get("some/path"), testPluginConfiguration.getExtraDirectory());
  }
}
