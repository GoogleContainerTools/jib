/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.api.LogEvent.Level;
import com.google.cloud.tools.jib.http.TestWebServer;
import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Future;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link UpdateChecker}. */
@RunWith(MockitoJUnitRunner.class)
public class UpdateCheckerTest {

  @Rule public final RestoreSystemProperties systemPropertyRestorer = new RestoreSystemProperties();
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private TestWebServer testWebServer;
  private Path configDir;

  @Before
  public void setUp()
      throws InterruptedException, GeneralSecurityException, URISyntaxException, IOException {
    testWebServer =
        new TestWebServer(
            false,
            Collections.singletonList(
                "HTTP/1.1 200 OK\nContent-Length:18\n\n{\"latest\":\"2.0.0\"}"),
            1);
    configDir = temporaryFolder.getRoot().toPath();
  }

  @After
  public void tearDown() throws IOException {
    testWebServer.close();
  }

  @Test
  public void testPerformUpdateCheck_newVersionFound() throws IOException {
    Instant before = Instant.now();
    setupConfigAndLastUpdateCheck();
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            ignored -> {}, "1.0.2", testWebServer.getEndpoint(), configDir, "tool name");
    Assert.assertTrue(testWebServer.getInputRead().contains("User-Agent: jib 1.0.2 tool name"));
    Assert.assertTrue(message.isPresent());
    Assert.assertEquals(
        "A new version of Jib (2.0.0) is available (currently using 1.0.2). Update your build "
            + "configuration to use the latest features and fixes!",
        message.get());
    String modifiedTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);
    Assert.assertTrue(Instant.parse(modifiedTime).isAfter(before));
  }

  @Test
  public void testPerformUpdateCheck_newJsonField()
      throws IOException, InterruptedException, GeneralSecurityException, URISyntaxException {
    try (TestWebServer server =
        new TestWebServer(
            false,
            Collections.singletonList(
                "HTTP/1.1 200 OK\nContent-Length:43\n\n{\"latest\":\"2.0.0\",\"unknownField\":\"unknown\"}"),
            1)) {
      setupConfigAndLastUpdateCheck();
      Optional<String> message =
          UpdateChecker.performUpdateCheck(
              ignored -> {}, "1.0.2", server.getEndpoint(), configDir, "tool name");
      Assert.assertTrue(message.isPresent());
      Assert.assertEquals(
          "A new version of Jib (2.0.0) is available (currently using 1.0.2). Update your build "
              + "configuration to use the latest features and fixes!",
          message.get());
    }
  }

  @Test
  public void testPerformUpdateCheck_onLatest() throws IOException {
    Instant before = Instant.now();
    setupConfigAndLastUpdateCheck();
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            ignored -> {}, "2.0.0", testWebServer.getEndpoint(), configDir, "tool name");
    Assert.assertFalse(message.isPresent());
    String modifiedTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);
    Assert.assertTrue(testWebServer.getInputRead().contains("User-Agent: jib 2.0.0 tool name"));
    Assert.assertTrue(Instant.parse(modifiedTime).isAfter(before));
  }

  @Test
  public void testPerformUpdateCheck_noConfigOrLastUpdateCheck() throws IOException {
    Instant before = Instant.now();
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            ignored -> {}, "1.0.2", testWebServer.getEndpoint(), configDir, "tool name");
    Assert.assertTrue(message.isPresent());
    Assert.assertEquals(
        "A new version of Jib (2.0.0) is available (currently using 1.0.2). Update your build "
            + "configuration to use the latest features and fixes!",
        message.get());
    String modifiedTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);
    Assert.assertTrue(Instant.parse(modifiedTime).isAfter(before));
  }

  @Test
  public void testPerformUpdateCheck_emptyConfigAndLastUpdateCheck() throws IOException {
    Files.createFile(configDir.resolve("config.json"));
    Files.createFile(configDir.resolve("lastUpdateCheck"));
    Instant before = Instant.now();
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            ignored -> {}, "1.0.2", testWebServer.getEndpoint(), configDir, "tool name");
    Assert.assertTrue(message.isPresent());
    Assert.assertEquals(
        "A new version of Jib (2.0.0) is available (currently using 1.0.2). Update your build "
            + "configuration to use the latest features and fixes!",
        message.get());
    String modifiedTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);
    Assert.assertTrue(Instant.parse(modifiedTime).isAfter(before));
  }

  @Test
  public void testPerformUpdateCheck_lastUpdateCheckTooSoon() throws IOException {
    FileTime modifiedTime = FileTime.from(Instant.now().minusSeconds(12));
    setupConfigAndLastUpdateCheck();
    Files.write(
        configDir.resolve("lastUpdateCheck"),
        modifiedTime.toString().getBytes(StandardCharsets.UTF_8));
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            ignored -> {}, "1.0.2", testWebServer.getEndpoint(), configDir, "tool name");
    Assert.assertFalse(message.isPresent());

    // lastUpdateCheck should not have changed
    String lastUpdateTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);
    Assert.assertEquals(Instant.parse(lastUpdateTime), modifiedTime.toInstant());
  }

  @Test
  public void testPerformUpdateCheck_systemProperty() {
    System.setProperty(PropertyNames.DISABLE_UPDATE_CHECKS, "true");
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            ignored -> {}, "1.0.2", testWebServer.getEndpoint(), configDir, "tool name");
    Assert.assertFalse(message.isPresent());
  }

  @Test
  public void testPerformUpdateCheck_configDisabled() throws IOException {
    Files.write(
        configDir.resolve("config.json"),
        "{\"disableUpdateCheck\":true}".getBytes(StandardCharsets.UTF_8));
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            ignored -> {}, "1.0.2", testWebServer.getEndpoint(), configDir, "tool name");
    Assert.assertFalse(message.isPresent());
  }

  @Test
  public void testPerformUpdateCheck_badConfig() throws IOException {
    Files.write(
        configDir.resolve("config.json"), "corrupt config".getBytes(StandardCharsets.UTF_8));
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            ignored -> {}, "1.0.2", testWebServer.getEndpoint(), configDir, "tool name");
    Assert.assertFalse(message.isPresent());
  }

  @Test
  public void testPerformUpdateCheck_badLastUpdateTime() throws IOException {
    Instant before = Instant.now();
    Files.write(
        configDir.resolve("lastUpdateCheck"), "bad timestamp".getBytes(StandardCharsets.UTF_8));
    Optional<String> message =
        UpdateChecker.performUpdateCheck(
            ignored -> {}, "1.0.2", testWebServer.getEndpoint(), configDir, "tool name");
    String modifiedTime =
        new String(
            Files.readAllBytes(configDir.resolve("lastUpdateCheck")), StandardCharsets.UTF_8);
    Assert.assertTrue(Instant.parse(modifiedTime).isAfter(before));
    Assert.assertTrue(message.isPresent());
    Assert.assertEquals(
        "A new version of Jib (2.0.0) is available (currently using 1.0.2). Update your build "
            + "configuration to use the latest features and fixes!",
        message.get());
  }

  @Test
  public void testPerformUpdateCheck_failSilently()
      throws InterruptedException, GeneralSecurityException, URISyntaxException, IOException {
    try (TestWebServer badServer =
        new TestWebServer(
            false,
            Collections.singletonList("HTTP/1.1 400 Bad Request\nContent-Length: 0\n\n"),
            1)) {
      Optional<String> message =
          UpdateChecker.performUpdateCheck(
              logEvent -> {
                Assert.assertEquals(Level.DEBUG, logEvent.getLevel());
                MatcherAssert.assertThat(
                    logEvent.getMessage(), CoreMatchers.containsString("Update check failed; "));
              },
              "1.0.2",
              badServer.getEndpoint(),
              configDir,
              "tool name");
      Assert.assertFalse(message.isPresent());
    }
  }

  @Test
  public void testFinishUpdateCheck_success() {
    Future<Optional<String>> updateCheckFuture = Futures.immediateFuture(Optional.of("Hello"));
    Optional<String> result = UpdateChecker.finishUpdateCheck(updateCheckFuture);
    Assert.assertTrue(result.isPresent());
    Assert.assertEquals("Hello", result.get());
  }

  @Test
  public void testFinishUpdateCheck_notDone() {
    @SuppressWarnings("unchecked")
    Future<Optional<String>> updateCheckFuture = Mockito.mock(Future.class);
    Mockito.when(updateCheckFuture.isDone()).thenReturn(false);

    Optional<String> result = UpdateChecker.finishUpdateCheck(updateCheckFuture);
    Assert.assertFalse(result.isPresent());
  }

  private void setupConfigAndLastUpdateCheck() throws IOException {
    Files.write(
        configDir.resolve("config.json"),
        "{\"disableUpdateCheck\":false}".getBytes(StandardCharsets.UTF_8));
    Files.write(
        configDir.resolve("lastUpdateCheck"),
        Instant.now().minus(Duration.ofDays(2)).toString().getBytes(StandardCharsets.UTF_8));
  }
}
