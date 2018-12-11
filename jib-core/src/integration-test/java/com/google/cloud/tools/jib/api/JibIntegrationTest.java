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

package com.google.cloud.tools.jib.api;

import com.google.cloud.tools.jib.Command;
import com.google.cloud.tools.jib.configuration.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.configuration.credentials.Credential;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.LocalRegistry;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/** Integration tests for {@link Jib}. */
public class JibIntegrationTest {

  @ClassRule
  public static final LocalRegistry localRegistry = new LocalRegistry(5000, "username", "password");

  /**
   * Pulls a built image and attempts to run it.
   *
   * @param imageReference the image reference of the built image
   * @return the container output
   * @throws IOException if an I/O exception occurs
   * @throws InterruptedException if the process was interrupted
   */
  private static String pullAndRunBuiltImage(String imageReference)
      throws IOException, InterruptedException {
    localRegistry.pull(imageReference);
    return new Command("docker", "run", "--rm", imageReference).run();
  }

  @Before
  public void setUp() {
    System.setProperty("sendCredentialsOverHttp", "true");
  }

  @After
  public void tearDown() {
    System.clearProperty("sendCredentialsOverHttp");
  }

  @Test
  public void testBasic_helloWorld()
      throws InvalidImageReferenceException, InterruptedException, ExecutionException,
          CacheDirectoryCreationException, IOException {
    ImageReference targetImageReference =
        ImageReference.of("localhost:5000", "jib-core", "basic-helloworld");
    JibContainer jibContainer =
        Jib.from("busybox")
            .setEntrypoint("echo", "Hello World")
            .containerize(
                Containerizer.to(
                        RegistryImage.named(targetImageReference)
                            .addCredentialRetriever(
                                () -> Optional.of(Credential.basic("username", "password"))))
                    .setAllowInsecureRegistries(true));

    Assert.assertEquals("Hello World\n", pullAndRunBuiltImage(targetImageReference.toString()));
    Assert.assertEquals(
        "Hello World\n",
        pullAndRunBuiltImage(
            targetImageReference.withTag(jibContainer.getDigest().toString()).toString()));
  }

  /** Ensure that a provided executor is not disposed. */
  @Test
  public void testProvidedExecutorNotDisposed()
      throws InvalidImageReferenceException, InterruptedException, ExecutionException,
          CacheDirectoryCreationException, IOException {
    ImageReference targetImageReference =
        ImageReference.of("localhost:5000", "jib-core", "basic-helloworld");
    Containerizer containerizer =
        Containerizer.to(
                RegistryImage.named(targetImageReference)
                    .addCredentialRetriever(
                        () -> Optional.of(Credential.basic("username", "password"))))
            .setAllowInsecureRegistries(true);

    ExecutorService executorService = Executors.newCachedThreadPool();
    containerizer.setExecutorService(executorService);
    Jib.from("busybox").setEntrypoint("echo", "Hello World").containerize(containerizer);
    Assert.assertFalse(executorService.isShutdown());

    executorService.shutdown();
  }
}
