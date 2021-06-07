/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.cli;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.cli.logging.CliLogger;
import com.google.cloud.tools.jib.cli.war.WarFiles;
import com.google.cloud.tools.jib.plugins.common.globalconfig.GlobalConfig;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.Futures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import picocli.CommandLine;

@CommandLine.Command(
    name = "war",
    mixinStandardHelpOptions = true,
    showAtFileInUsageHelp = true,
    description = "Containerize a war")
public class War implements Callable<Integer> {
  @CommandLine.Spec
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private CommandLine.Model.CommandSpec spec;

  @CommandLine.Mixin
  @VisibleForTesting
  @SuppressWarnings("NullAway.Init") // initialized by picocli
          CommonCliOptions commonCliOptions;

  @CommandLine.Mixin
  @VisibleForTesting
  @SuppressWarnings("NullAway.Init") // initialized by picocli
          CommonArtifactCommandOptions commonArtifactCommandOptions;

  @CommandLine.Parameters(description = "The path to the war file (ex: path/to/my-war.war)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Path warFile;

  @CommandLine.Option(
          names = "--app-root",
          paramLabel = "<app root>",
          description = "The app root on the container")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private Path appRoot;

  @Override
  public Integer call() {
    commonCliOptions.validate();
    SingleThreadedExecutor executor = new SingleThreadedExecutor();
    ConsoleLogger logger =
            CliLogger.newLogger(
                    commonCliOptions.getVerbosity(),
                    commonCliOptions.getHttpTrace(),
                    commonCliOptions.getConsoleOutput(),
                    spec.commandLine().getOut(),
                    spec.commandLine().getErr(),
                    executor);
    Future<Optional<String>> updateCheckFuture = Futures.immediateFuture(Optional.empty());
    try {
      JibCli.configureHttpLogging(commonCliOptions.getHttpTrace().toJulLevel());
      GlobalConfig globalConfig = GlobalConfig.readConfig();
      updateCheckFuture =
              JibCli.newUpdateChecker(
                      globalConfig,
                      commonCliOptions.getVerbosity(),
                      logEvent -> logger.log(logEvent.getLevel(), logEvent.getMessage()));
      if (!Files.exists(warFile)) {
        logger.log(LogEvent.Level.ERROR, "The file path provided does not exist: " + warFile);
        return 1;
      }
      if (Files.isDirectory(warFile)) {
        logger.log(
                LogEvent.Level.ERROR,
                "The file path provided is for a directory. Please provide a path to a WAR: "
                        + warFile);
        return 1;
      }
      CacheDirectories cacheDirectories =
              CacheDirectories.from(commonCliOptions, warFile.toAbsolutePath().getParent());
      ArtifactProcessor processor =
              ArtifactProcessors.fromWar(warFile, cacheDirectories, this, commonArtifactCommandOptions);
      JibContainerBuilder containerBuilder =
              WarFiles.toJibContainerBuilder(
                      processor, commonCliOptions, commonArtifactCommandOptions, logger);
      Containerizer containerizer = Containerizers.from(commonCliOptions, logger, cacheDirectories);

      // Enable registry mirrors
      Multimaps.asMap(globalConfig.getRegistryMirrors()).forEach(containerizer::addRegistryMirrors);

      JibContainer jibContainer = containerBuilder.containerize(containerizer);
      JibCli.writeImageJson(commonCliOptions.getImageJsonPath(), jibContainer);
    } catch (Exception ex) {
      JibCli.logTerminatingException(logger, ex, commonCliOptions.isStacktrace());
      return 1;
    } finally {
      JibCli.finishUpdateChecker(logger, updateCheckFuture);
      executor.shutDownAndAwaitTermination(Duration.ofSeconds(3));
    }
    return 0;
  }

  /**
   * Returns the user-specified app root in the container.
   *
   * @return a user configured app root
   */
  public Optional<AbsoluteUnixPath> getAppRoot() {
    if (appRoot == null) {
      return Optional.empty();
    }
    return Optional.of(AbsoluteUnixPath.fromPath(appRoot));
  }
}
