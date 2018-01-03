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

import com.google.api.client.http.HttpTransport;
import com.google.cloud.tools.crepecake.image.json.ManifestTemplate;
import com.google.cloud.tools.crepecake.image.json.V22ManifestTemplate;
import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link ManifestPusher}. */
public class ManifestPusherIntegrationTest {

  @ClassRule public static LocalRegistry localRegistry = new LocalRegistry(5000);

  public static void enableLogging() {
    Logger logger = Logger.getLogger(HttpTransport.class.getName());
    logger.setLevel(Level.CONFIG);
    logger.addHandler(
        new Handler() {

          @Override
          public void close() throws SecurityException {}

          @Override
          public void flush() {}

          @Override
          public void publish(LogRecord record) {
            // Default ConsoleHandler will print >= INFO to System.err.
            if (record.getLevel().intValue() < Level.INFO.intValue()) {
              System.out.println(record.getMessage());
            }
          }
        });
  }

  @Test
  public void testPush() throws IOException, RegistryException {
    RegistryClient registryClient = new RegistryClient(null, "gcr.io", "distroless/java");
    ManifestTemplate manifestTemplate = registryClient.pullManifest("latest");

    enableLogging();

    registryClient = new RegistryClient(null, "localhost:5000", "busybox");
    registryClient.pushManifest((V22ManifestTemplate) manifestTemplate, "latest");
  }
}
