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
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.cli.cli2.logging.CliLogger;
import com.google.cloud.tools.jib.cli.jar.JarFiles;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

@CommandLine.Command(name = "jar", showAtFileInUsageHelp = true, description = "Containerize a jar")
public class Jar implements Callable<Integer> {

  @CommandLine.Spec private CommandSpec spec = CommandSpec.create();

  @CommandLine.ParentCommand
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  protected JibCli globalOptions;

  @CommandLine.Parameters(description = "The path to the jar file (ex: path/to/my-jar.jar)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Path jarFile;

  @CommandLine.Option(
      names = "--mode",
      defaultValue = "exploded",
      paramLabel = "<mode>",
      description = "The jar processing mode, candidates: packaged, default: exploded")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private String mode;

  @Override
  public Integer call() {
    globalOptions.validate();
    SingleThreadedExecutor executor = new SingleThreadedExecutor();
    try (TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider()) {

      ConsoleLogger logger =
          CliLogger.newLogger(
              globalOptions.getVerbosity(),
              globalOptions.getConsoleOutput(),
              spec.commandLine().getOut(),
              spec.commandLine().getErr(),
              executor);

      if (!Files.exists(jarFile)) {
        logger.log(LogEvent.Level.ERROR, "The file path provided does not exist: " + jarFile);
        return 1;
      }
      if (Files.isDirectory(jarFile)) {
        logger.log(
            LogEvent.Level.ERROR,
            "The file path provided is for a directory. Please provide a path to a jar file: "
                + jarFile);
        return 1;
      }

      Containerizer containerizer = Containerizers.from(globalOptions, logger);

      JibContainerBuilder containerBuilder =
          JarFiles.toJibContainerBuilder(jarFile, tempDirectoryProvider.newDirectory(), mode);

      containerBuilder.containerize(containerizer);
    } catch (Exception ex) {
      if (globalOptions.isStacktrace()) {
        ex.printStackTrace();
      }
      System.err.println(ex.getClass().getName() + ": " + ex.getMessage());
      return 1;
    } finally {
      executor.shutDownAndAwaitTermination(Duration.ofSeconds(3));
    }
    return 0;
  }
}
