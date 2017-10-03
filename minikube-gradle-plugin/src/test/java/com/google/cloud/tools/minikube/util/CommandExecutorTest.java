/*
 * Copyright 2017 Google Inc.
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

package com.google.cloud.tools.minikube.util;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.tools.ant.filters.StringInputStream;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for CommandExecutor */
public class CommandExecutorTest {
  @Mock private CommandExecutor.ProcessBuilderFactory processBuilderFactoryMock;

  @Mock private ProcessBuilder processBuilderMock;

  @Mock private Process processMock;

  @Mock private Logger loggerMock;

  private InOrder loggerInOrder;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);

    when(processBuilderFactoryMock.createProcessBuilder()).thenReturn(processBuilderMock);
    when(processBuilderMock.start()).thenReturn(processMock);
    loggerInOrder = inOrder(loggerMock);
  }

  @Test
  public void testRunCommand_success() throws IOException, InterruptedException {
    List<String> command = Arrays.asList("someCommand", "someOption");
    List<String> expectedOutput = Arrays.asList("some output line 1", "some output line 2");

    // Has the mocked process output the expected output.
    setProcessMockOutput(expectedOutput);

    // Executes the command.
    List<String> output =
        new CommandExecutor().setProcessBuilderFactory(processBuilderFactoryMock).run(command);

    // Verifies the process building and output reading is correct.
    verifyProcessBuilding(command);
    Assert.assertEquals(expectedOutput, output);

    verifyZeroInteractions(loggerMock);
  }

  @Test
  public void testRunCommandWithLogging_success() throws IOException, InterruptedException {
    List<String> command = Arrays.asList("someCommand", "someOption");
    List<String> expectedOutput = Arrays.asList("some output line 1", "some output line 2");

    setProcessMockOutput(expectedOutput);

    List<String> output =
        new CommandExecutor()
            .setLogger(loggerMock)
            .setProcessBuilderFactory(processBuilderFactoryMock)
            .run(command);

    verifyProcessBuilding(command);
    Assert.assertEquals(expectedOutput, output);

    // Verifies the logger messages were logged.
    loggerInOrder.verify(loggerMock).debug("Running command : someCommand someOption");
    loggerInOrder.verify(loggerMock).lifecycle("some output line 1");
    loggerInOrder.verify(loggerMock).lifecycle("some output line 2");
  }

  @Test
  public void testRunCommandWithLogging_badProcessOutput()
      throws IOException, InterruptedException {
    List<String> command = Arrays.asList("someCommand", "someOption");

    InputStream processOutput = new StringInputStream("");
    processOutput.close();
    when(processMock.getInputStream()).thenReturn(processOutput);

    new CommandExecutor()
        .setLogger(loggerMock)
        .setProcessBuilderFactory(processBuilderFactoryMock)
        .run(command);

    loggerInOrder.verify(loggerMock).warn("IO Exception reading process output");
  }

  @Test
  public void testRunCommand_commandError() throws IOException, InterruptedException {
    List<String> command = Arrays.asList("someCommand", "someOption");
    List<String> expectedOutput = Arrays.asList("some output line 1", "some output line 2");

    setProcessMockOutput(expectedOutput);
    when(processMock.waitFor()).thenReturn(1);

    try {
      new CommandExecutor().setProcessBuilderFactory(processBuilderFactoryMock).run(command);
      Assert.fail("Expected a GradleException to be thrown");
    } catch (GradleException ex) {
      Assert.assertEquals("command exited with non-zero exit code : 1", ex.getMessage());

      verifyProcessBuilding(command);
      verifyZeroInteractions(loggerMock);
    }
  }

  @Test
  public void testRunCommandWithLogging_commandTimeout() throws IOException, InterruptedException {
    List<String> command = Arrays.asList("someCommand", "someOption");

    // Mocks the ExecutorService to be interrupted when awaiting termination.
    CommandExecutor.ExecutorServiceFactory executorServiceFactoryMock =
        mock(CommandExecutor.ExecutorServiceFactory.class);
    ExecutorService executorServiceMock = mock(ExecutorService.class);
    when(executorServiceFactoryMock.createExecutorService()).thenReturn(executorServiceMock);
    when(executorServiceMock.awaitTermination(CommandExecutor.TIMEOUT_SECONDS, TimeUnit.SECONDS))
        .thenThrow(new InterruptedException());

    new CommandExecutor()
        .setLogger(loggerMock)
        .setProcessBuilderFactory(processBuilderFactoryMock)
        .setExecutorServiceFactory(executorServiceFactoryMock)
        .run(command);

    loggerInOrder.verify(loggerMock).debug("Running command : someCommand someOption");
    loggerInOrder
        .verify(loggerMock)
        .debug("Task Executor interrupted waiting for output consumer thread");
  }

  private void setProcessMockOutput(List<String> expectedOutput) {
    when(processMock.getInputStream())
        .thenReturn(new StringInputStream(String.join("\n", expectedOutput)));
  }

  private void verifyProcessBuilding(List<String> command) throws IOException {
    verify(processBuilderMock).command(command);
    verify(processBuilderMock).redirectErrorStream(true);
    verify(processBuilderMock).start();

    verify(processMock).getInputStream();
  }
}
