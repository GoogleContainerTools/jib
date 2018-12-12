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

package com.google.cloud.tools.jib.plugins.common;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link AnsiLoggerWithFooter}. */
public class AnsiLoggerWithFooterTest {

  private static final Duration FUTURE_TIMEOUT = Duration.ofSeconds(1);

  private final StringBuilder logBuilder = new StringBuilder();
  private final AnsiLoggerWithFooter testAnsiLoggerWithFooter =
      new AnsiLoggerWithFooter(logBuilder::append);

  @Test
  public void testLog_noFooter() throws InterruptedException, ExecutionException, TimeoutException {
    testAnsiLoggerWithFooter
        .log(() -> logBuilder.append("message\n"))
        .get(FUTURE_TIMEOUT.getSeconds(), TimeUnit.SECONDS);

    Assert.assertEquals("\033[0Jmessage\n", logBuilder.toString());
  }

  @Test
  public void testLog_sameFooter()
      throws InterruptedException, ExecutionException, TimeoutException {
    testAnsiLoggerWithFooter
        .setFooter(Collections.singletonList("footer"))
        .get(FUTURE_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
    testAnsiLoggerWithFooter
        .log(() -> logBuilder.append("message\n"))
        .get(FUTURE_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
    testAnsiLoggerWithFooter
        .log(() -> logBuilder.append("another message\n"))
        .get(FUTURE_TIMEOUT.getSeconds(), TimeUnit.SECONDS);

    Assert.assertEquals(
        "\033[0J"
            + "\033[1mfooter\033[0m"
            + "\033[1A\033[0J"
            + "message\n"
            + "\033[1mfooter\033[0m"
            + "\033[1A\033[0J"
            + "another message\n"
            + "\033[1mfooter\033[0m",
        logBuilder.toString());
  }

  @Test
  public void testLog_changingFooter()
      throws InterruptedException, ExecutionException, TimeoutException {
    testAnsiLoggerWithFooter
        .setFooter(Collections.singletonList("footer"))
        .get(FUTURE_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
    testAnsiLoggerWithFooter
        .log(() -> logBuilder.append("message\n"))
        .get(FUTURE_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
    testAnsiLoggerWithFooter
        .setFooter(Arrays.asList("two line", "footer"))
        .get(FUTURE_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
    testAnsiLoggerWithFooter
        .log(() -> logBuilder.append("another message\n"))
        .get(FUTURE_TIMEOUT.getSeconds(), TimeUnit.SECONDS);

    Assert.assertEquals(
        "\033[0J"
            + "\033[1mfooter\033[0m"
            + "\033[1A\033[0J"
            + "message\n"
            + "\033[1mfooter\033[0m"
            + "\033[1A\033[0J"
            + "\033[1mtwo line\033[0m\n"
            + "\033[1mfooter\033[0m"
            + "\033[1A\033[1A\033[0J"
            + "another message\n"
            + "\033[1mtwo line\033[0m\n"
            + "\033[1mfooter\033[0m",
        logBuilder.toString());
  }
}
