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

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.JibEventType;
import com.google.cloud.tools.jib.event.events.LogEvent;
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.plugins.common.JavaContainerBuilderHelper;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.cloud.tools.jib.plugins.common.TimerEventHandler;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLoggerBuilder;
import com.google.cloud.tools.jib.plugins.common.logging.ProgressDisplayGenerator;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
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
   * @param session the {@link MavenSession} for the plugin.
   * @param log the Maven {@link Log} to log messages during Jib execution
   * @param appRoot root directory in the image where the app will be placed
   * @return a MavenProjectProperties from the given project and logger.
   */
  static MavenProjectProperties getForProject(
      MavenProject project, MavenSession session, Log log, AbsoluteUnixPath appRoot) {
    return new MavenProjectProperties(project, session, log, appRoot);
  }

  /**
   * Gets a system property with the given name. First checks for a -D commandline argument, then
   * checks for a property defined in the POM, then returns null if neither are defined.
   *
   * @param propertyName the name of the system property
   * @param project the Maven project
   * @param session the Maven session
   * @return the value of the system property, or null if not defined
   */
  @Nullable
  public static String getProperty(
      String propertyName, @Nullable MavenProject project, @Nullable MavenSession session) {
    if (session != null && session.getSystemProperties().containsKey(propertyName)) {
      return session.getSystemProperties().getProperty(propertyName);
    }
    if (project != null && project.getProperties().containsKey(propertyName)) {
      return project.getProperties().getProperty(propertyName);
    }
    return null;
  }

  private static EventHandlers makeEventHandlers(
      MavenSession session, Log log, SingleThreadedExecutor singleThreadedExecutor) {
    ConsoleLoggerBuilder logEventHandlerBuilder =
        (isProgressFooterEnabled(session)
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

    return EventHandlers.builder()
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
                            update.getProgress(), update.getUnfinishedLeafTasks()))))
        .build();
  }

  @VisibleForTesting
  static boolean isProgressFooterEnabled(MavenSession session) {
    if (!session.getRequest().isInteractiveMode()) {
      return false;
    }

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

  /**
   * Gets the major version number from a Java version string.
   *
   * <p>Examples: {@code "1.7" -> 7, "1.8.0_161" -> 8, "10" -> 10, "11.0.1" -> 11}
   *
   * @param versionString the string to convert
   * @return the major version number as an integer, or 0 if the string is invalid
   */
  @VisibleForTesting
  static int getVersionFromString(String versionString) {
    // Parse version starting with "1."
    if (versionString.startsWith("1.")) {
      if (versionString.length() >= 3 && Character.isDigit(versionString.charAt(2))) {
        return versionString.charAt(2) - '0';
      }
      return 0;
    }

    // Parse string starting with major version number
    int dotIndex = versionString.indexOf(".");
    try {
      if (dotIndex == -1) {
        return Integer.parseInt(versionString);
      }
      return Integer.parseInt(versionString.substring(0, versionString.indexOf(".")));
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private final MavenProject project;
  private final MavenSession session;
  private final SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();
  private final EventHandlers eventHandlers;
  private final AbsoluteUnixPath appRoot;

  @VisibleForTesting
  MavenProjectProperties(
      MavenProject project, MavenSession session, Log log, AbsoluteUnixPath appRoot) {
    this.project = project;
    this.appRoot = appRoot;
    this.session = session;
    eventHandlers = makeEventHandlers(session, log, singleThreadedExecutor);
  }

  @Override
  public JibContainerBuilder createContainerBuilder(RegistryImage baseImage) throws IOException {
    try {
      if (isWarProject()) {
        Path explodedWarPath =
            Paths.get(project.getBuild().getDirectory()).resolve(project.getBuild().getFinalName());
        return JavaContainerBuilderHelper.fromExplodedWar(baseImage, explodedWarPath, appRoot);
      }

      Path classesOutputDirectory = Paths.get(project.getBuild().getOutputDirectory());
      Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");

      // Add dependencies, resources, and classes
      return JavaContainerBuilder.from(baseImage)
          .setAppRoot(appRoot)
          .addResources(classesOutputDirectory, isClassFile.negate())
          .addClasses(classesOutputDirectory, isClassFile)
          .addDependencies(
              project
                  .getArtifacts()
                  .stream()
                  .map(Artifact::getFile)
                  .map(File::toPath)
                  .collect(Collectors.toList()))
          .toContainerBuilder();

    } catch (IOException ex) {
      throw new IOException(
          "Obtaining project build output files failed; make sure you have compiled your project "
              + "before trying to build the image. (Did you accidentally run \"mvn clean "
              + "jib:build\" instead of \"mvn clean compile jib:build\"?)",
          ex);
    }
  }

  @Override
  public List<Path> getClassFiles() throws IOException {
    return new DirectoryWalker(Paths.get(project.getBuild().getOutputDirectory())).walk().asList();
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
  public boolean isWarProject() {
    return MojoCommon.isWarProject(project);
  }

  @Override
  public String getName() {
    return project.getArtifactId();
  }

  @Override
  public String getVersion() {
    return project.getVersion();
  }

  @Override
  public int getMajorJavaVersion() {
    // Check properties for version
    if (project.getProperties().getProperty("maven.compiler.target") != null) {
      return getVersionFromString(project.getProperties().getProperty("maven.compiler.target"));
    }
    if (project.getProperties().getProperty("maven.compiler.release") != null) {
      return getVersionFromString(project.getProperties().getProperty("maven.compiler.release"));
    }

    // Check maven-compiler-plugin for version
    Plugin mavenCompilerPlugin =
        project.getPlugin("org.apache.maven.plugins:maven-compiler-plugin");
    if (mavenCompilerPlugin != null) {
      Xpp3Dom pluginConfiguration = (Xpp3Dom) mavenCompilerPlugin.getConfiguration();
      if (pluginConfiguration != null) {
        Xpp3Dom target = pluginConfiguration.getChild("target");
        if (target != null) {
          return getVersionFromString(target.getValue());
        }
        Xpp3Dom release = pluginConfiguration.getChild("release");
        if (release != null) {
          return getVersionFromString(release.getValue());
        }
      }
    }
    return 6; // maven-compiler-plugin default is 1.6
  }

  @Override
  public boolean isOffline() {
    return session.isOffline();
  }
}
