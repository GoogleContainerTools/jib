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

package com.google.cloud.tools.jib.plugins.common.logging;

import com.google.cloud.tools.jib.api.LogEvent.Level;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLoggerBuilder.ConsoleLoggerFactory;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests for {@link ConsoleLoggerBuilder}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsoleLoggerBuilderTest {

  @Mock private Consumer<String> mockLifecycleConsumer;
  @Mock private Consumer<String> mockProgressConsumer;
  @Mock private Consumer<String> mockInfoConsumer;
  @Mock private Consumer<String> mockDebugConsumer;
  @Mock private Consumer<String> mockWarnConsumer;
  @Mock private Consumer<String> mockErrorConsumer;

  @Test
  void testBuild() {
    List<String> messages = new ArrayList<>();
    List<Level> levels = new ArrayList<>();

    ConsoleLoggerFactory consoleLoggerFactory =
        messageConsumers -> {
          Assert.assertEquals(
              ImmutableMap.builder()
                  .put(Level.LIFECYCLE, mockLifecycleConsumer)
                  .put(Level.PROGRESS, mockProgressConsumer)
                  .put(Level.INFO, mockInfoConsumer)
                  .put(Level.DEBUG, mockDebugConsumer)
                  .put(Level.WARN, mockWarnConsumer)
                  .put(Level.ERROR, mockErrorConsumer)
                  .build(),
              messageConsumers);

          return new ConsoleLogger() {

            @Override
            public void log(Level logLevel, String message) {
              messages.add(message);
              levels.add(logLevel);
            }

            @Override
            public void setFooter(List<String> footerLines) {
              // No op.
            }
          };
        };

    ConsoleLogger consoleLogger =
        new ConsoleLoggerBuilder(consoleLoggerFactory)
            .lifecycle(mockLifecycleConsumer)
            .progress(mockProgressConsumer)
            .info(mockInfoConsumer)
            .debug(mockDebugConsumer)
            .warn(mockWarnConsumer)
            .error(mockErrorConsumer)
            .build();

    consoleLogger.log(Level.LIFECYCLE, "lifecycle");
    consoleLogger.log(Level.PROGRESS, "progress");
    consoleLogger.log(Level.INFO, "info");
    consoleLogger.log(Level.DEBUG, "debug");
    consoleLogger.log(Level.WARN, "warn");
    consoleLogger.log(Level.ERROR, "error");

    Assert.assertEquals(
        Arrays.asList(
            Level.LIFECYCLE, Level.PROGRESS, Level.INFO, Level.DEBUG, Level.WARN, Level.ERROR),
        levels);
    Assert.assertEquals(
        Arrays.asList("lifecycle", "progress", "info", "debug", "warn", "error"), messages);
  }
}
