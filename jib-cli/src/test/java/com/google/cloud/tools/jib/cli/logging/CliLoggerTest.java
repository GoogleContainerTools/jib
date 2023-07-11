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

package com.google.cloud.tools.jib.cli.logging;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.api.LogEvent.Level;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import java.io.PrintWriter;
import java.time.Duration;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link CliLogger}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CliLoggerTest {

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

  @Rule public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @Mock private PrintWriter mockOut;
  @Mock private PrintWriter mockErr;

  private void createLoggerAndSendMessages(Verbosity verbosity, ConsoleOutput consoleOutput) {
    SingleThreadedExecutor executor = new SingleThreadedExecutor();
    ConsoleLogger logger =
        CliLogger.newLogger(
            verbosity, HttpTraceLevel.off, consoleOutput, mockOut, mockErr, executor);

    logger.log(Level.DEBUG, "debug");
    logger.log(Level.INFO, "info");
    logger.log(Level.LIFECYCLE, "lifecycle");
    logger.log(Level.PROGRESS, "progress");
    logger.log(Level.WARN, "warn");
    logger.log(Level.ERROR, "error");

    executor.shutDownAndAwaitTermination(Duration.ofSeconds(3));
  }

  @Test
  void testLog_quiet_plainConsole() {
    createLoggerAndSendMessages(Verbosity.quiet, ConsoleOutput.plain);

    Mockito.verifyNoInteractions(mockOut);
    Mockito.verifyNoInteractions(mockErr);
  }

  @Test
  void testLog_error_plainConsole() {
    createLoggerAndSendMessages(Verbosity.error, ConsoleOutput.plain);

    Mockito.verify(mockErr).println("[ERROR] error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verifyNoInteractions(mockOut);
  }

  @Test
  void testLog_warn_plainConsole() {
    createLoggerAndSendMessages(Verbosity.warn, ConsoleOutput.plain);

    Mockito.verify(mockErr).println("[ERROR] error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("[WARN] warn");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  void testLog_lifecycle_plainConsole() {
    createLoggerAndSendMessages(Verbosity.lifecycle, ConsoleOutput.plain);

    Mockito.verify(mockErr).println("[ERROR] error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("[WARN] warn");
    Mockito.verify(mockOut).println("lifecycle");
    Mockito.verify(mockOut).println("progress");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  void testLog_info_plainConsole() {
    createLoggerAndSendMessages(Verbosity.info, ConsoleOutput.plain);

    Mockito.verify(mockErr).println("[ERROR] error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("[WARN] warn");
    Mockito.verify(mockOut).println("lifecycle");
    Mockito.verify(mockOut).println("progress");
    Mockito.verify(mockOut).println("info");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  void testLog_debug_plainConsole() {
    createLoggerAndSendMessages(Verbosity.debug, ConsoleOutput.plain);

    Mockito.verify(mockErr).println("[ERROR] error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("[WARN] warn");
    Mockito.verify(mockOut).println("lifecycle");
    Mockito.verify(mockOut).println("progress");
    Mockito.verify(mockOut).println("info");
    Mockito.verify(mockOut).println("debug");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  void testLog_quiet_richConsole() {
    createLoggerAndSendMessages(Verbosity.quiet, ConsoleOutput.rich);

    Mockito.verifyNoInteractions(mockOut);
    Mockito.verifyNoInteractions(mockErr);
  }

  @Test
  void testLog_error_richConsole() {
    createLoggerAndSendMessages(Verbosity.error, ConsoleOutput.rich);

    Mockito.verify(mockErr).println("[ERROR] error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verifyNoInteractions(mockOut);
  }

  @Test
  void testLog_warn_richConsole() {
    createLoggerAndSendMessages(Verbosity.warn, ConsoleOutput.rich);

    Mockito.verify(mockErr).println("[ERROR] error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("[WARN] warn");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  void testLog_lifecycle_richConsole() {
    createLoggerAndSendMessages(Verbosity.lifecycle, ConsoleOutput.rich);

    Mockito.verify(mockErr).println("[ERROR] error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("[WARN] warn");
    Mockito.verify(mockOut).println("lifecycle");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  void testLog_info_richConsole() {
    createLoggerAndSendMessages(Verbosity.info, ConsoleOutput.rich);

    Mockito.verify(mockErr).println("[ERROR] error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("[WARN] warn");
    Mockito.verify(mockOut).println("lifecycle");
    Mockito.verify(mockOut).println("info");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  void testLog_debug_richConsole() {
    createLoggerAndSendMessages(Verbosity.debug, ConsoleOutput.rich);

    Mockito.verify(mockErr).println("[ERROR] error");
    Mockito.verifyNoMoreInteractions(mockErr);
    Mockito.verify(mockOut).println("[WARN] warn");
    Mockito.verify(mockOut).println("lifecycle");
    Mockito.verify(mockOut).println("info");
    Mockito.verify(mockOut).println("debug");
    Mockito.verifyNoMoreInteractions(mockOut);
  }

  @Test
  void testIsRichConsole_true() {
    assertThat(CliLogger.isRichConsole(ConsoleOutput.rich, HttpTraceLevel.off)).isTrue();
  }

  @Test
  void testIsRichConsole_falseIfHttpTrace() {
    assertThat(CliLogger.isRichConsole(ConsoleOutput.rich, HttpTraceLevel.config)).isFalse();
  }

  @Test
  void testIsRichConsole_false() {
    assertThat(CliLogger.isRichConsole(ConsoleOutput.plain, HttpTraceLevel.off)).isFalse();
  }

  @Test
  void testIsRightConsole_autoWindowsTrue() {
    System.setProperty("os.name", "windows");
    assertThat(CliLogger.isRichConsole(ConsoleOutput.auto, HttpTraceLevel.off)).isTrue();
  }

  @Test
  void testIsRightConsole_autoDumbTermFalse() {
    environmentVariables.set("TERM", "dumb");
    assertThat(CliLogger.isRichConsole(ConsoleOutput.auto, HttpTraceLevel.off)).isFalse();
  }
}
