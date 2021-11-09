/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.cli;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.jib.ProjectInfo;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.cli.logging.Verbosity;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.plugins.common.ImageMetadataOutput;
import com.google.cloud.tools.jib.plugins.common.globalconfig.GlobalConfig;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JibCliTest {

  @Mock private GlobalConfig globalConfig;
  @Mock private ConsoleLogger logger;

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private JibContainer mockJibContainer;

  @Test
  public void testConfigureHttpLogging() {
    Logger logger = JibCli.configureHttpLogging(Level.ALL);
    assertThat(logger.getName()).isEqualTo("com.google.api.client.http.HttpTransport");
    assertThat(logger.getLevel()).isEqualTo(Level.ALL);

    assertThat(logger.getHandlers()).hasLength(1);
    Handler handler = logger.getHandlers()[0];
    assertThat(handler).isInstanceOf(ConsoleHandler.class);
    assertThat(handler.getLevel()).isEqualTo(Level.ALL);
  }

  @Test
  public void testLogTerminatingException() {
    JibCli.logTerminatingException(logger, new IOException("test error message"), false);

    verify(logger)
        .log(LogEvent.Level.ERROR, "\u001B[31;1mjava.io.IOException: test error message\u001B[0m");
    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testLogTerminatingException_stackTrace() {
    JibCli.logTerminatingException(logger, new IOException("test error message"), true);

    String stackTraceLine =
        "at com.google.cloud.tools.jib.cli.JibCliTest.testLogTerminatingException_stackTrace";
    verify(logger).log(eq(LogEvent.Level.ERROR), contains(stackTraceLine));
    verify(logger)
        .log(LogEvent.Level.ERROR, "\u001B[31;1mjava.io.IOException: test error message\u001B[0m");
    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testNewUpdateChecker_noUpdateCheck() throws ExecutionException, InterruptedException {
    when(globalConfig.isDisableUpdateCheck()).thenReturn(true);
    Future<Optional<String>> updateChecker =
        JibCli.newUpdateChecker(globalConfig, Verbosity.info, ignored -> {});
    assertThat(updateChecker.get()).isEmpty();
  }

  @Test
  public void testFinishUpdateChecker_correctMessageLogged() {
    Future<Optional<String>> updateCheckFuture = Futures.immediateFuture(Optional.of("2.0.0"));
    JibCli.finishUpdateChecker(logger, updateCheckFuture);
    verify(logger)
        .log(
            eq(LogEvent.Level.LIFECYCLE),
            contains(
                "A new version of Jib CLI (2.0.0) is available (currently using "
                    + VersionInfo.getVersionSimple()
                    + "). Download the latest Jib CLI version from "
                    + ProjectInfo.GITHUB_URL
                    + "/releases/tag/v2.0.0-cli"));
  }

  @Test
  public void testWriteImageJson()
      throws InvalidImageReferenceException, IOException, DigestException {
    String imageId = "sha256:61bb3ec31a47cb730eb58a38bbfa813761a51dca69d10e39c24c3d00a7b2c7a9";
    String digest = "sha256:3f1be7e19129edb202c071a659a4db35280ab2bb1a16f223bfd5d1948657b6fc";
    when(mockJibContainer.getTargetImage())
        .thenReturn(ImageReference.parse("eclipse-temurin:8-jre"));
    when(mockJibContainer.getImageId()).thenReturn(DescriptorDigest.fromDigest(imageId));
    when(mockJibContainer.getDigest()).thenReturn(DescriptorDigest.fromDigest(digest));
    when(mockJibContainer.getTags()).thenReturn(ImmutableSet.of("latest", "tag-2"));

    Path outputPath = temporaryFolder.getRoot().toPath().resolve("jib-image.json");
    JibCli.writeImageJson(Optional.of(outputPath), mockJibContainer);

    String outputJson = new String(Files.readAllBytes(outputPath), StandardCharsets.UTF_8);
    ImageMetadataOutput metadataOutput =
        JsonTemplateMapper.readJson(outputJson, ImageMetadataOutput.class);
    assertThat(metadataOutput.getImage()).isEqualTo("eclipse-temurin:8-jre");
    assertThat(metadataOutput.getImageId()).isEqualTo(imageId);
    assertThat(metadataOutput.getImageDigest()).isEqualTo(digest);
    assertThat(metadataOutput.getTags()).containsExactly("latest", "tag-2");
  }
}
