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

import java.util.Map;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

public class BuildConfigurationTest {

  @Test
  public void testBuilder() {
    String expectedBaseImageServerUrl = "someserver";
    String expectedBaseImageName = "baseimage";
    String expectedBaseImageTag = "baseimagetag";
    String expectedTargetServerUrl = "someotherserver";
    String expectedTargetImageName = "targetimage";
    String expectedTargetTag = "targettag";
    String expectedCredentialHelperName = "credentialhelper";
    String expectedMainClass = "mainclass";

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder()
            .setBaseImageServerUrl(expectedBaseImageServerUrl)
            .setBaseImageName(expectedBaseImageName)
            .setBaseImageTag(expectedBaseImageTag)
            .setTargetServerUrl(expectedTargetServerUrl)
            .setTargetImageName(expectedTargetImageName)
            .setTargetTag(expectedTargetTag)
            .setCredentialHelperName(expectedCredentialHelperName)
            .setMainClass(expectedMainClass)
            .build();
    Assert.assertEquals(expectedBaseImageServerUrl, buildConfiguration.getBaseImageServerUrl());
    Assert.assertEquals(expectedBaseImageName, buildConfiguration.getBaseImageName());
    Assert.assertEquals(expectedBaseImageTag, buildConfiguration.getBaseImageTag());
    Assert.assertEquals(expectedTargetServerUrl, buildConfiguration.getTargetServerUrl());
    Assert.assertEquals(expectedTargetImageName, buildConfiguration.getTargetImageName());
    Assert.assertEquals(expectedTargetTag, buildConfiguration.getTargetTag());
    Assert.assertEquals(expectedCredentialHelperName, buildConfiguration.getCredentialHelperName());
    Assert.assertEquals(expectedMainClass, buildConfiguration.getMainClass());
  }

  @Test
  public void testBuilder_missingValues() {
    try {
      BuildConfiguration.builder().build();
      Assert.fail("Build configuration should not be built with missing values");

    } catch (IllegalStateException ex) {
      for (Map.Entry<BuildConfiguration.Fields, String> description :
          BuildConfiguration.Builder.FIELD_DESCRIPTIONS.entrySet()) {
        if (!description.getKey().isRequired()) {
          continue;
        }
        Assert.assertThat(ex.getMessage(), CoreMatchers.containsString(description.getValue()));
      }
    }
  }
}
