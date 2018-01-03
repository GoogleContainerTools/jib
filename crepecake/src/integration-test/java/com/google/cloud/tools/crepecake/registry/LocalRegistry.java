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

package com.google.cloud.tools.crepecake.registry;

import java.io.IOException;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;

/** {@link TestRule} that runs a local registry. */
class LocalRegistry extends ExternalResource {

  private final int port;

  LocalRegistry(int port) {
    this.port = port;
  }

  /** Starts the local registry. */
  @Override
  protected void before() throws Throwable {
    String runRegistryCommand =
        "docker run -d -p " + port + ":5000 --restart=always --name registry registry:2";
    Runtime.getRuntime().exec(runRegistryCommand).waitFor();

    String pullImageCommand = "docker pull busybox";
    Runtime.getRuntime().exec(pullImageCommand).waitFor();

    String tagImageCommand = "docker tag busybox localhost:" + port + "/busybox";
    Runtime.getRuntime().exec(tagImageCommand).waitFor();

    String pushImageCommand = "docker push localhost:" + port + "/busybox";
    Runtime.getRuntime().exec(pushImageCommand).waitFor();
  }

  /** Stops the local registry. */
  @Override
  protected void after() {
    try {
      String stopRegistryCommand = "docker stop registry";
      Runtime.getRuntime().exec(stopRegistryCommand).waitFor();

      String removeRegistryContainerCommand = "docker rm -v registry";
      Runtime.getRuntime().exec(removeRegistryContainerCommand).waitFor();

    } catch (InterruptedException | IOException ex) {
      throw new RuntimeException("Could not stop local registry fully", ex);
    }
  }
}
