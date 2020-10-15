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

package com.google.cloud.tools.jib.cli.cli2.logging;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import java.io.PrintStream;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link CliLogger}. */
@RunWith(MockitoJUnitRunner.class)
public class CliLoggerTest {

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

  @Rule public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @Mock private PrintStream mockOut;
  @Mock private PrintStream mockErr;

  @Mock private CliLogger mockCliLogger;

  private void sendMessages(CliLogger logger) {
    logger.debug("debug");
    logger.info("info");
    logger.lifecycle("lifecycle");
    logger.warn("warn");
    logger.error("error");
  }

  @Test
  public void testLog_quiet() {
    CliLogger logger = new CliLogger(Verbosity.quiet, mockOut, mockErr);
    sendMessages(logger);
    Mockito.verifyNoInteractions(mockOut);
    Mockito.verifyNoInteractions(mockErr);
  }

  @Test
  public void testLog_error() {
    CliLogger logger = new CliLogger(Verbosity.error, mockOut, mockErr);
    sendMessages(logger);
    Mockito.verify(mockErr).println("error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verifyNoInteractions(mockOut);
  }

  @Test
  public void testLog_warn() {
    CliLogger logger = new CliLogger(Verbosity.warn, mockOut, mockErr);
    sendMessages(logger);
    Mockito.verify(mockErr).println("error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("warn");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  public void testLog_lifecycle() {
    CliLogger logger = new CliLogger(Verbosity.lifecycle, mockOut, mockErr);
    sendMessages(logger);
    Mockito.verify(mockErr).println("error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("warn");
    Mockito.verify(mockOut).println("lifecycle");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  public void testLog_info() {
    CliLogger logger = new CliLogger(Verbosity.info, mockOut, mockErr);
    sendMessages(logger);
    Mockito.verify(mockErr).println("error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("warn");
    Mockito.verify(mockOut).println("lifecycle");
    Mockito.verify(mockOut).println("info");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  public void testLog_debug() {
    CliLogger logger = new CliLogger(Verbosity.debug, mockOut, mockErr);
    sendMessages(logger);
    Mockito.verify(mockErr).println("error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("warn");
    Mockito.verify(mockOut).println("lifecycle");
    Mockito.verify(mockOut).println("info");
    Mockito.verify(mockOut).println("debug");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  public void testIsRichConsole_true() {
    assertThat(CliLogger.isRichConsole(ConsoleOutput.rich)).isTrue();
  }

  @Test
  public void testIsRichConsole_false() {
    assertThat(CliLogger.isRichConsole(ConsoleOutput.plain)).isFalse();
  }

  @Test
  public void testIsRightConsole_autoWindowsTrue() {
    System.setProperty("os.name", "windows");
    assertThat(CliLogger.isRichConsole(ConsoleOutput.auto)).isTrue();
  }

  @Test
  public void testIsRightConsole_autoTermTrue() {
    environmentVariables.set("TERM", "not-dumb");
    assertThat(CliLogger.isRichConsole(ConsoleOutput.auto)).isTrue();
  }

  @Test
  public void testIsRightConsole_autoDumbTermFalse() {
    environmentVariables.set("TERM", "dumb");
    assertThat(CliLogger.isRichConsole(ConsoleOutput.auto)).isFalse();
  }

  @Test
  public void testNewLogger_richConfig() {
    InOrder inOrder = Mockito.inOrder(mockCliLogger);
    SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();
    ConsoleLogger logger = CliLogger.newLogger(mockCliLogger, true, singleThreadedExecutor);

    logger.log(LogEvent.Level.DEBUG, "debug");
    logger.log(LogEvent.Level.INFO, "info");
    logger.log(LogEvent.Level.LIFECYCLE, "lifecycle");
    logger.log(LogEvent.Level.PROGRESS, "progress");
    logger.log(LogEvent.Level.WARN, "warn");
    logger.log(LogEvent.Level.ERROR, "error");

    singleThreadedExecutor.shutDownAndAwaitTermination(Duration.ofSeconds(3));

    inOrder.verify(mockCliLogger).debug("debug");
    inOrder.verify(mockCliLogger).info("info");
    inOrder.verify(mockCliLogger).lifecycle("lifecycle");
    inOrder.verify(mockCliLogger).warn("warn");
    inOrder.verify(mockCliLogger).error("error");
    inOrder.verifyNoMoreInteractions(); // progress is not configured
  }

  @Test
  public void testNewLogger_plainConfig() {
    InOrder inOrder = Mockito.inOrder(mockCliLogger);
    SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();
    ConsoleLogger logger = CliLogger.newLogger(mockCliLogger, false, singleThreadedExecutor);

    logger.log(LogEvent.Level.DEBUG, "debug");
    logger.log(LogEvent.Level.INFO, "info");
    logger.log(LogEvent.Level.LIFECYCLE, "lifecycle");
    logger.log(LogEvent.Level.PROGRESS, "progress");
    logger.log(LogEvent.Level.WARN, "warn");
    logger.log(LogEvent.Level.ERROR, "error");

    singleThreadedExecutor.shutDownAndAwaitTermination(Duration.ofSeconds(3));

    inOrder.verify(mockCliLogger).debug("debug");
    inOrder.verify(mockCliLogger).info("info");
    inOrder.verify(mockCliLogger).lifecycle("lifecycle");
    inOrder.verify(mockCliLogger).lifecycle("progress");
    inOrder.verify(mockCliLogger).warn("warn");
    inOrder.verify(mockCliLogger).error("error");
    inOrder.verifyNoMoreInteractions();
  }
}
