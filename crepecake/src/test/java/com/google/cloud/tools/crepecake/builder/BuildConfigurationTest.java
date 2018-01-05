/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.crepecake.builder;

import org.junit.Assert;
import org.junit.Test;

public class BuildConfigurationTest {

  @Test
  public void testBuilder() {
    String expectedBaseImageServerUrl = "someserver";
    String expectedBaseImageName = "baseimage";
    String expectedTargetServerUrl = "someotherserver";
    String expectedTargetImageName = "targetimage";
    String expectedTargetTag = "targettag";
    String expectedCredentialHelperName = "credentialhelper";

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder()
            .setBaseImageServerUrl(expectedBaseImageServerUrl)
            .setBaseImageName(expectedBaseImageName)
            .setTargetServerUrl(expectedTargetServerUrl)
            .setTargetImageName(expectedTargetImageName)
            .setTargetTag(expectedTargetTag)
            .setCredentialHelperName(expectedCredentialHelperName)
            .build();
    Assert.assertEquals(expectedBaseImageName, buildConfiguration.getBaseImageName());
  }
}
