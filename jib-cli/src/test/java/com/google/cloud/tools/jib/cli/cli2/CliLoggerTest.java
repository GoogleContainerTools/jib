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

package com.google.cloud.tools.jib.cli.cli2;

import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLoggerBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link CliLogger}. */
@RunWith(MockitoJUnitRunner.class)
public class CliLoggerTest {

  @Mock ConsoleLoggerBuilder mockBuilder;

  @Test
  public void testNewConsoleLogger_error() {
    CliLogger.newLogger(mockBuilder, JibCli.Verbosity.error);
    Mockito.verify(mockBuilder).error(Mockito.any());
    Mockito.verify(mockBuilder).build();
    Mockito.verifyNoMoreInteractions(mockBuilder);
  }

  @Test
  public void testNewConsoleLogger_warn() {
    CliLogger.newLogger(mockBuilder, JibCli.Verbosity.warn);
    Mockito.verify(mockBuilder).error(Mockito.any());
    Mockito.verify(mockBuilder).warn(Mockito.any());
    Mockito.verify(mockBuilder).build();
    Mockito.verifyNoMoreInteractions(mockBuilder);
  }

  @Test
  public void testNewConsoleLogger_lifecycle() {
    CliLogger.newLogger(mockBuilder, JibCli.Verbosity.lifecycle);
    Mockito.verify(mockBuilder).error(Mockito.any());
    Mockito.verify(mockBuilder).warn(Mockito.any());
    Mockito.verify(mockBuilder).lifecycle(Mockito.any());
    Mockito.verify(mockBuilder).build();
    Mockito.verifyNoMoreInteractions(mockBuilder);
  }

  @Test
  public void testNewConsoleLogger_info() {
    CliLogger.newLogger(mockBuilder, JibCli.Verbosity.info);
    Mockito.verify(mockBuilder).error(Mockito.any());
    Mockito.verify(mockBuilder).warn(Mockito.any());
    Mockito.verify(mockBuilder).lifecycle(Mockito.any());
    Mockito.verify(mockBuilder).info(Mockito.any());
    Mockito.verify(mockBuilder).build();
    Mockito.verifyNoMoreInteractions(mockBuilder);
  }

  @Test
  public void testNewConsoleLogger_debug() {
    CliLogger.newLogger(mockBuilder, JibCli.Verbosity.debug);
    Mockito.verify(mockBuilder).error(Mockito.any());
    Mockito.verify(mockBuilder).warn(Mockito.any());
    Mockito.verify(mockBuilder).lifecycle(Mockito.any());
    Mockito.verify(mockBuilder).info(Mockito.any());
    Mockito.verify(mockBuilder).debug(Mockito.any());
    Mockito.verify(mockBuilder).build();
    Mockito.verifyNoMoreInteractions(mockBuilder);
  }
}
