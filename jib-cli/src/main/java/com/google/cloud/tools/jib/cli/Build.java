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

package com.google.cloud.tools.jib.cli;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.cli.buildfile.BuildFiles;
import com.google.cloud.tools.jib.cli.logging.CliLogger;
import com.google.cloud.tools.jib.plugins.common.globalconfig.GlobalConfig;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.Futures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

@CommandLine.Command(
    name = "build",
    mixinStandardHelpOptions = true,
    showAtFileInUsageHelp = true,
    description = "Build a container")
public class Build implements Callable<Integer> {

  @CommandLine.Spec
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  private CommandSpec spec;

  @CommandLine.Mixin
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  @VisibleForTesting
  CommonCliOptions commonCliOptions;

  @VisibleForTesting
  @CommandLine.Option(
      names = {"-c", "--context"},
      defaultValue = ".",
      paramLabel = "<project-root>",
      description = "The context root directory of the build (ex: path/to/my/build/things)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  Path contextRoot;

  @VisibleForTesting
  @CommandLine.Option(
      names = {"-b", "--build-file"},
      paramLabel = "<build-file>",
      description = "The path to the build file (ex: path/to/other-jib.yaml)")
  @SuppressWarnings("NullAway.Init") // initialized by picocli
  Path buildFileUnprocessed;

  @CommandLine.Option(
      names = {"-p", "--parameter"},
      paramLabel = "<name>=<value>",
      description =
          "templating parameter to inject into build file, replace $${<name>} with <value> (repeatable)")
  private Map<String, String> templateParameters = Collections.emptyMap();

  /**
   * Returns a user configured Path to a buildfile and if none is configured returns jib.yaml in
   * {@link #contextRoot}.
   *
   * @return a path to a buildfile
   */
  @VisibleForTesting
  Path getBuildFile() {
    if (buildFileUnprocessed == null) {
      return contextRoot.resolve("jib.yaml");
    }
    return buildFileUnprocessed;
  }

  public Map<String, String> getTemplateParameters() {
    return templateParameters;
  }

  @Override
  public Integer call() {
    commonCliOptions.validate();
    Path buildFile = getBuildFile();
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
      if (!Files.isReadable(buildFile)) {
        logger.log(
            LogEvent.Level.ERROR,
            "The Build File YAML either does not exist or cannot be opened for reading: "
                + buildFile);
        return 1;
      }
      if (!Files.isRegularFile(buildFile)) {
        logger.log(LogEvent.Level.ERROR, "Build File YAML path is a not a file: " + buildFile);
        return 1;
      }

      CacheDirectories cacheDirectories = CacheDirectories.from(commonCliOptions, contextRoot);
      Containerizer containerizer = Containerizers.from(commonCliOptions, logger, cacheDirectories);

      JibContainerBuilder containerBuilder =
          BuildFiles.toJibContainerBuilder(contextRoot, buildFile, this, commonCliOptions, logger);

      // Enable registry mirrors
      Multimaps.asMap(globalConfig.getRegistryMirrors()).forEach(containerizer::addRegistryMirrors);

      containerBuilder.containerize(containerizer);
    } catch (Exception ex) {
      JibCli.logTerminatingException(logger, ex, commonCliOptions.isStacktrace());
      return 1;
    } finally {
      JibCli.finishUpdateCheck(logger, updateCheckFuture);
      executor.shutDownAndAwaitTermination(Duration.ofSeconds(3));
    }
    return 0;
  }
}
