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

package com.google.cloud.tools.jib;

import com.google.common.base.Strings;
import org.junit.Assert;

/** Configuration for integration tests. */
public class IntegrationTestingConfiguration {

  public static String getTestRepositoryLocation() {
    String projectId = System.getenv("JIB_INTEGRATION_TESTING_PROJECT");
    //    System.out.println("JIB_INTEGRATION_TESTING_PROJECT: " + projectId);
    if (!Strings.isNullOrEmpty(projectId)) {
      return "gcr.io/" + projectId;
    }
    String location = System.getenv("JIB_INTEGRATION_TESTING_LOCATION");
    if (Strings.isNullOrEmpty(location)) {
      Assert.fail(
          "Must set environment variable JIB_INTEGRATION_TESTING_PROJECT to the "
              + "GCP project to use for integration testing or "
              + "JIB_INTEGRATION_TESTING_LOCATION to a suitable registry/repository location.");
    }
    return location;
  }

  private IntegrationTestingConfiguration() {}
}
