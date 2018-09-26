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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link JibPluginConfiguration}. */
public class JibPluginConfigurationTest {

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
    Assert.assertEquals("/app", testPluginConfiguration.getAppRoot());
  }
}
