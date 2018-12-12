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

import com.google.common.util.concurrent.MoreExecutors;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link AnsiLoggerWithFooter}. */
public class AnsiLoggerWithFooterTest {

  private final StringBuilder logBuilder = new StringBuilder();
  private final AnsiLoggerWithFooter testAnsiLoggerWithFooter =
      new AnsiLoggerWithFooter(
          this::logBuilderPrinter, true, MoreExecutors.newDirectExecutorService());

  @Test
  public void testLog_noFooter() {
    testAnsiLoggerWithFooter.log(this::logBuilderPrinter, "message");

    Assert.assertEquals("message\n", logBuilder.toString());
  }

  @Test
  public void testLog_footerDisabled() {
    AnsiLoggerWithFooter testAnsiLoggerWithFooter =
        new AnsiLoggerWithFooter(
            this::logBuilderPrinter, false, MoreExecutors.newDirectExecutorService());

    testAnsiLoggerWithFooter.setFooter(Collections.singletonList("footer"));
    testAnsiLoggerWithFooter.log(this::logBuilderPrinter, "message");
    testAnsiLoggerWithFooter.setFooter(Arrays.asList("two line", "footer"));
    testAnsiLoggerWithFooter.log(this::logBuilderPrinter, "another message");

    Assert.assertEquals("message\nanother message\n", logBuilder.toString());
  }

  @Test
  public void testLog_sameFooter() {
    testAnsiLoggerWithFooter.setFooter(Collections.singletonList("footer"));

    Assert.assertEquals("\033[1mfooter\033[0m\n", logBuilder.toString());

    testAnsiLoggerWithFooter.log(this::logBuilderPrinter, "message");

    Assert.assertEquals(
        "\033[1mfooter\033[0m\n"
            + "\033[1A\033[0J\n"
            + "\033[1Amessage\n"
            + "\033[1mfooter\033[0m\n",
        logBuilder.toString());

    testAnsiLoggerWithFooter.log(this::logBuilderPrinter, "another message");

    Assert.assertEquals(
        "\033[1mfooter\033[0m\n"
            + "\033[1A\033[0J\n"
            + "\033[1Amessage\n"
            + "\033[1mfooter\033[0m\n"
            + "\033[1A\033[0J\n"
            + "\033[1Aanother message\n"
            + "\033[1mfooter\033[0m\n",
        logBuilder.toString());
  }

  @Test
  public void testLog_changingFooter() {
    testAnsiLoggerWithFooter.setFooter(Collections.singletonList("footer"));
    testAnsiLoggerWithFooter.log(this::logBuilderPrinter, "message");

    testAnsiLoggerWithFooter.setFooter(Arrays.asList("two line", "footer"));

    Assert.assertEquals(
        "\033[1mfooter\033[0m\n"
            + "\033[1A\033[0J\n"
            + "\033[1Amessage\n"
            + "\033[1mfooter\033[0m\n"
            + "\033[1A\033[0J\n"
            + "\033[1A\033[1mtwo line\033[0m\n\033[1mfooter\033[0m\n",
        logBuilder.toString());

    testAnsiLoggerWithFooter.log(this::logBuilderPrinter, "another message");

    Assert.assertEquals(
        "\033[1mfooter\033[0m\n"
            + "\033[1A\033[0J\n"
            + "\033[1Amessage\n"
            + "\033[1mfooter\033[0m\n"
            + "\033[1A\033[0J\n"
            + "\033[1A\033[1mtwo line\033[0m\n\033[1mfooter\033[0m\n"
            + "\033[1A\033[1A\033[0J\n"
            + "\033[1Aanother message\n"
            + "\033[1mtwo line\033[0m\n\033[1mfooter\033[0m\n",
        logBuilder.toString());
  }

  // This mimics a real log printer that always adds a new line at the end.
  private void logBuilderPrinter(String message) {
    logBuilder.append(message).append('\n');
  }
}
