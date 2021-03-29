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
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.cli.logging.Verbosity;
import com.google.cloud.tools.jib.plugins.common.globalconfig.GlobalConfig;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.common.util.concurrent.Futures;
import java.io.IOException;
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

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private GlobalConfig globalConfig;
  @Mock private ConsoleLogger logger;

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
  public void testFinishUpdateChecker_correctMessageReturned() {
    Future<Optional<String>> updateCheckFuture = Futures.immediateFuture(Optional.of("2.0.0"));
    JibCli.finishUpdateChecker(logger, updateCheckFuture);
    verify(logger)
        .log(
            eq(LogEvent.Level.LIFECYCLE),
            contains(
                "A new version of jib-cli (2.0.0) is available (currently using "
                    + VersionInfo.getVersionSimple()
                    + "). Download the latest jib-cli version from "
                    + ProjectInfo.GITHUB_URL
                    + "/releases/tag/v2.0.0-cli"));
  }
}
