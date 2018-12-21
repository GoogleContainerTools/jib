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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.configuration.FilePermissions;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.JibEventType;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.cloud.tools.jib.plugins.common.TimerEventHandler;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLoggerBuilder;
import com.google.cloud.tools.jib.plugins.common.logging.ProgressDisplayGenerator;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.Os;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/** Obtains information about a {@link MavenProject}. */
public class MavenProjectProperties implements ProjectProperties {

  /** Used for logging during main class inference and analysis of user configuration. */
  public static final String PLUGIN_NAME = "jib-maven-plugin";

  /** Used to identify this plugin when interacting with the maven system. */
  public static final String PLUGIN_KEY = "com.google.cloud.tools:" + PLUGIN_NAME;

  /** Used to generate the User-Agent header and history metadata. */
  private static final String TOOL_NAME = "jib-maven-plugin";

  /** Used for logging during main class inference. */
  private static final String JAR_PLUGIN_NAME = "'maven-jar-plugin'";

  /**
   * @param project the {@link MavenProject} for the plugin.
   * @param containerizeWar whether to do WAR containerization
   * @param log the Maven {@link Log} to log messages during Jib execution
   * @param extraDirectory path to the directory for the extra files layer
   * @param permissions map from path on container to file permissions for extra-layer files
   * @param appRoot root directory in the image where the app will be placed
   * @return a MavenProjectProperties from the given project and logger.
   * @throws MojoExecutionException if no class files are found in the output directory.
   */
  static MavenProjectProperties getForProject(
      MavenProject project,
      boolean containerizeWar,
      Log log,
      Path extraDirectory,
      Map<AbsoluteUnixPath, FilePermissions> permissions,
      AbsoluteUnixPath appRoot)
      throws MojoExecutionException {
    try {
      return new MavenProjectProperties(
          project,
          log,
          MavenLayerConfigurations.getForProject(
              project, containerizeWar, extraDirectory, permissions, appRoot));

    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Obtaining project build output files failed; make sure you have compiled your project "
              + "before trying to build the image. (Did you accidentally run \"mvn clean "
              + "jib:build\" instead of \"mvn clean compile jib:build\"?)",
          ex);
    }
  }

  private static EventHandlers makeEventHandlers(
      Log log, SingleThreadedExecutor singleThreadedExecutor) {
    ConsoleLoggerBuilder logEventHandlerBuilder =
        (isProgressFooterEnabled()
                ? ConsoleLoggerBuilder.rich(singleThreadedExecutor)
                : ConsoleLoggerBuilder.plain(singleThreadedExecutor).progress(log::info))
            .lifecycle(log::info);
    if (log.isDebugEnabled()) {
      logEventHandlerBuilder
          .debug(log::debug)
          // INFO messages also go to Log#debug since Log#info is used for LIFECYCLE.
          .info(log::debug);
    }
    if (log.isWarnEnabled()) {
      logEventHandlerBuilder.warn(log::warn);
    }
    if (log.isErrorEnabled()) {
      logEventHandlerBuilder.error(log::error);
    }
    ConsoleLogger consoleLogger = logEventHandlerBuilder.build();

    return new EventHandlers()
        .add(
            JibEventType.LOGGING,
            logEvent -> consoleLogger.log(logEvent.getLevel(), logEvent.getMessage()))
        .add(
            JibEventType.TIMING,
            new TimerEventHandler(message -> consoleLogger.log(LogEvent.Level.DEBUG, message)))
        .add(
            JibEventType.PROGRESS,
            new ProgressEventHandler(
                update ->
                    consoleLogger.setFooter(
                        ProgressDisplayGenerator.generateProgressDisplay(
                            update.getProgress(), update.getUnfinishedAllocations()))));
  }

  private static boolean isProgressFooterEnabled() {
    // TODO: Consolidate with GradleProjectProperties?
    if ("plain".equals(System.getProperty(PropertyNames.CONSOLE))) {
      return false;
    }

    // Enables progress footer when ANSI is supported (Windows or System.console() not null and TERM
    // not 'dumb').
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
      return true;
    }
    return System.console() != null && !"dumb".equals(System.getenv("TERM"));
  }

  private final MavenProject project;
  private final SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();
  private final EventHandlers eventHandlers;
  private final JavaLayerConfigurations javaLayerConfigurations;

  @VisibleForTesting
  MavenProjectProperties(
      MavenProject project, Log log, JavaLayerConfigurations javaLayerConfigurations) {
    this.project = project;
    this.javaLayerConfigurations = javaLayerConfigurations;

    eventHandlers = makeEventHandlers(log, singleThreadedExecutor);
  }

  @Override
  public JavaLayerConfigurations getJavaLayerConfigurations() {
    return javaLayerConfigurations;
  }

  @Override
  public void waitForLoggingThread() {
    singleThreadedExecutor.shutDownAndAwaitTermination();
  }

  @Override
  public EventHandlers getEventHandlers() {
    return eventHandlers;
  }

  @Override
  public String getToolName() {
    return TOOL_NAME;
  }

  @Override
  public String getPluginName() {
    return PLUGIN_NAME;
  }

  @Nullable
  @Override
  public String getMainClassFromJar() {
    Plugin mavenJarPlugin = project.getPlugin("org.apache.maven.plugins:maven-jar-plugin");
    if (mavenJarPlugin != null) {
      Xpp3Dom jarConfiguration = (Xpp3Dom) mavenJarPlugin.getConfiguration();
      if (jarConfiguration == null) {
        return null;
      }
      Xpp3Dom archiveObject = jarConfiguration.getChild("archive");
      if (archiveObject == null) {
        return null;
      }
      Xpp3Dom manifestObject = archiveObject.getChild("manifest");
      if (manifestObject == null) {
        return null;
      }
      Xpp3Dom mainClassObject = manifestObject.getChild("mainClass");
      if (mainClassObject == null) {
        return null;
      }
      return mainClassObject.getValue();
    }
    return null;
  }

  @Override
  public Path getDefaultCacheDirectory() {
    return Paths.get(project.getBuild().getDirectory(), CACHE_DIRECTORY_NAME);
  }

  @Override
  public String getJarPluginName() {
    return JAR_PLUGIN_NAME;
  }

  @Override
  public String getName() {
    return project.getArtifactId();
  }

  @Override
  public String getVersion() {
    return project.getVersion();
  }
}
