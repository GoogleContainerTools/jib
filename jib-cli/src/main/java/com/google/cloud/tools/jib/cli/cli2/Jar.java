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
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "jar", showAtFileInUsageHelp = true, description = "Containerize a jar")
public class Jar implements Callable<Integer> {
  @CommandLine.ParentCommand
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  protected JibCli globalOptions;

  @CommandLine.Parameters(description = "The path to the jar file (ex: path/to/my-jar.jar)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Path jarFile;

  /**
   * Returns a user configured Path to a jar file.
   *
   * @return a path to a jar file
   */
  public Path getJarFile() {
    return jarFile;
  }

  @Override
  public Integer call() {
    globalOptions.validate();
    Path jarFile = getJarFile();
    try {
      ConsoleLogger logger =
          CliLogger.newLogger(globalOptions.getVerbosity(), globalOptions.getConsoleOutput());
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
          JarFiles.toJibContainerBuilder(getJarFile(), Paths.get("build-artifacts"));
      containerBuilder.containerize(containerizer);
      MoreFiles.deleteDirectoryContents(
          Paths.get("build-artifacts"), RecursiveDeleteOption.ALLOW_INSECURE);
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
