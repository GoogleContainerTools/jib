/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.minikube.util;

import java.util.*;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@code MinikubeDockerEnvParser} */
public class MinikubeDockerEnvParserTest {

  @Test
  public void testParse_success() {
    List<String> keyValueStrings =
        Arrays.asList(
            "SOME_VARIABLE_1=SOME_VALUE_1", "SOME_VARIABLE_2=SOME_VALUE_2", "SOME_VARIABLE_3=");
    Map<String, String> expectedEnvironment = new HashMap<>();
    expectedEnvironment.put("SOME_VARIABLE_1", "SOME_VALUE_1");
    expectedEnvironment.put("SOME_VARIABLE_2", "SOME_VALUE_2");
    expectedEnvironment.put("SOME_VARIABLE_3", "");

    Map<String, String> environment = MinikubeDockerEnvParser.parse(keyValueStrings);

    Assert.assertEquals(expectedEnvironment, environment);
  }

  @Test
  public void testParse_variableNameEmpty() {
    List<String> keyValueStrings =
        Arrays.asList("SOME_VARIABLE_1=SOME_VALUE_1", "=SOME_VALUE_2", "SOME_VARIABLE_3=");

    try {
      MinikubeDockerEnvParser.parse(keyValueStrings);
      Assert.fail("Expected an IllegalArgumentException to be thrown");
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "Error while parsing minikube's Docker environment: encountered empty environment variable name",
          ex.getMessage());
    }
  }

  @Test
  public void testParse_invalidFormat() {
    List<String> keyValueStrings =
        Arrays.asList("SOME_VARIABLE_1=SOME_VALUE_1", "SOME_VARIABLE_2", "SOME_VARIABLE_3=");

    try {
      MinikubeDockerEnvParser.parse(keyValueStrings);
      Assert.fail("Expected an IllegalArgumentException to be thrown");
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "Error while parsing minikube's Docker environment: environment variable string not in KEY=VALUE format",
          ex.getMessage());
    }
  }
}
