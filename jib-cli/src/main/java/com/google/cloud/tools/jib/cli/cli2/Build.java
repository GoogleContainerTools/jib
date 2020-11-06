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

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent.Level;
import com.google.cloud.tools.jib.cli.buildfile.BuildFiles;
import com.google.cloud.tools.jib.cli.cli2.logging.CliLogger;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

@CommandLine.Command(
    name = "build",
    showAtFileInUsageHelp = true,
    description = "Build a container")
public class Build implements Callable<Integer> {

  @CommandLine.Spec private CommandSpec spec = CommandSpec.create();

  @CommandLine.ParentCommand
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  protected JibCli globalOptions;

  @Override
  public Integer call() {
    globalOptions.validate();
    SingleThreadedExecutor executor = new SingleThreadedExecutor();
    try {
      ConsoleLogger logger =
          CliLogger.newLogger(
              globalOptions.getVerbosity(),
              globalOptions.getConsoleOutput(),
              spec.commandLine().getOut(),
              spec.commandLine().getErr(),
              executor);

      Path buildFile = globalOptions.getBuildFile();
      if (!Files.isReadable(buildFile)) {
        logger.log(
            Level.ERROR,
            "The Build File YAML either does not exist or cannot be opened for reading: "
                + buildFile);
        return 1;
      }
      if (!Files.isRegularFile(buildFile)) {
        logger.log(Level.ERROR, "Build File YAML path is a not a file: " + buildFile);
        return 1;
      }

      Containerizer containerizer = Containerizers.from(globalOptions, logger);

      JibContainerBuilder containerBuilder =
          BuildFiles.toJibContainerBuilder(
              globalOptions.getContextRoot(), buildFile, globalOptions, logger);

      containerBuilder.containerize(containerizer);
    } catch (Exception ex) {
      if (globalOptions.isStacktrace()) {
        ex.printStackTrace();
      }
      System.err.println(ex.getClass().getName() + ": " + ex.getMessage());
      return 1;
    }
    return 0;
  }
}
