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

import com.google.api.client.util.Lists;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JavaContainerBuilder;
import com.google.cloud.tools.jib.api.JavaContainerBuilder.LayerType;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.event.events.TimerEvent;
import com.google.cloud.tools.jib.event.progress.ProgressEventHandler;
import com.google.cloud.tools.jib.filesystem.DirectoryWalker;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.plugins.common.ContainerizingMode;
import com.google.cloud.tools.jib.plugins.common.JavaContainerBuilderHelper;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.cloud.tools.jib.plugins.common.PropertyNames;
import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtensionConfiguration;
import com.google.cloud.tools.jib.plugins.common.TimerEventHandler;
import com.google.cloud.tools.jib.plugins.common.ZipUtil;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLoggerBuilder;
import com.google.cloud.tools.jib.plugins.common.logging.ProgressDisplayGenerator;
import com.google.cloud.tools.jib.plugins.common.logging.SingleThreadedExecutor;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
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

  private static final Duration LOGGING_THREAD_SHUTDOWN_TIMEOUT = Duration.ofSeconds(1);

  /**
   * Static factory method for {@link MavenProjectProperties}.
   *
   * @param jibPluginDescriptor the jib-maven-plugin plugin descriptor
   * @param project the {@link MavenProject} for the plugin.
   * @param session the {@link MavenSession} for the plugin.
   * @param log the Maven {@link Log} to log messages during Jib execution
   * @param tempDirectoryProvider temporary directory provider
   * @return a MavenProjectProperties from the given project and logger.
   */
  public static MavenProjectProperties getForProject(
      PluginDescriptor jibPluginDescriptor,
      MavenProject project,
      MavenSession session,
      Log log,
      TempDirectoryProvider tempDirectoryProvider) {
    Preconditions.checkNotNull(jibPluginDescriptor);
    return new MavenProjectProperties(
        jibPluginDescriptor, project, session, log, tempDirectoryProvider);
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

  @VisibleForTesting
  static Optional<String> getChildValue(@Nullable Xpp3Dom dom, String... childNodePath) {
    if (dom == null) {
      return Optional.empty();
    }

    Xpp3Dom node = dom;
    for (String child : childNodePath) {
      node = node.getChild(child);
      if (node == null) {
        return Optional.empty();
      }
    }
    return Optional.ofNullable(node.getValue());
  }

  private final PluginDescriptor jibPluginDescriptor;
  private final MavenProject project;
  private final MavenSession session;
  private final SingleThreadedExecutor singleThreadedExecutor = new SingleThreadedExecutor();
  private final ConsoleLogger consoleLogger;
  private final TempDirectoryProvider tempDirectoryProvider;

  @VisibleForTesting
  MavenProjectProperties(
      PluginDescriptor jibPluginDescriptor,
      MavenProject project,
      MavenSession session,
      Log log,
      TempDirectoryProvider tempDirectoryProvider) {
    this.jibPluginDescriptor = jibPluginDescriptor;
    this.project = project;
    this.session = session;
    this.tempDirectoryProvider = tempDirectoryProvider;
    ConsoleLoggerBuilder consoleLoggerBuilder =
        (isProgressFooterEnabled(session)
                ? ConsoleLoggerBuilder.rich(singleThreadedExecutor, true)
                : ConsoleLoggerBuilder.plain(singleThreadedExecutor).progress(log::info))
            .lifecycle(log::info);
    if (log.isDebugEnabled()) {
      consoleLoggerBuilder
          .debug(log::debug)
          // INFO messages also go to Log#debug since Log#info is used for LIFECYCLE.
          .info(log::debug);
    }
    if (log.isWarnEnabled()) {
      consoleLoggerBuilder.warn(log::warn);
    }
    if (log.isErrorEnabled()) {
      consoleLoggerBuilder.error(log::error);
    }
    consoleLogger = consoleLoggerBuilder.build();
  }

  @Override
  public JibContainerBuilder createJibContainerBuilder(
      JavaContainerBuilder javaContainerBuilder, ContainerizingMode containerizingMode)
      throws IOException {
    try {
      if (isWarProject()) {
        Path war = getWarArtifact();
        Path explodedWarPath = tempDirectoryProvider.newDirectory();
        ZipUtil.unzip(war, explodedWarPath);
        return JavaContainerBuilderHelper.fromExplodedWar(javaContainerBuilder, explodedWarPath);
      }

      switch (containerizingMode) {
        case EXPLODED:
          // Add resources, and classes
          Path classesOutputDirectory = Paths.get(project.getBuild().getOutputDirectory());
          // Don't use Path.endsWith(), since Path works on path elements.
          Predicate<Path> isClassFile = path -> path.getFileName().toString().endsWith(".class");
          javaContainerBuilder
              .addResources(classesOutputDirectory, isClassFile.negate())
              .addClasses(classesOutputDirectory, isClassFile);
          break;

        case PACKAGED:
          // Add a JAR
          javaContainerBuilder.addToClasspath(getJarArtifact());
          break;

        default:
          throw new IllegalStateException("unknown containerizing mode: " + containerizingMode);
      }

      // Classify and add dependencies
      Map<LayerType, List<Path>> classifiedDependencies =
          classifyDependencies(
              project.getArtifacts(),
              session
                  .getProjects()
                  .stream()
                  .map(MavenProject::getArtifact)
                  .collect(Collectors.toSet()));

      javaContainerBuilder.addDependencies(
          Preconditions.checkNotNull(classifiedDependencies.get(LayerType.DEPENDENCIES)));
      javaContainerBuilder.addSnapshotDependencies(
          Preconditions.checkNotNull(classifiedDependencies.get(LayerType.SNAPSHOT_DEPENDENCIES)));
      javaContainerBuilder.addProjectDependencies(
          Preconditions.checkNotNull(classifiedDependencies.get(LayerType.PROJECT_DEPENDENCIES)));
      return javaContainerBuilder.toContainerBuilder();

    } catch (IOException ex) {
      throw new IOException(
          "Obtaining project build output files failed; make sure you have "
              + (containerizingMode == ContainerizingMode.PACKAGED ? "packaged" : "compiled")
              + " your project "
              + "before trying to build the image. (Did you accidentally run \"mvn clean "
              + "jib:build\" instead of \"mvn clean "
              + (containerizingMode == ContainerizingMode.PACKAGED ? "package" : "compile")
              + " jib:build\"?)",
          ex);
    }
  }

  @VisibleForTesting
  Map<LayerType, List<Path>> classifyDependencies(
      Set<Artifact> dependencies, Set<Artifact> projectArtifacts) {
    Map<LayerType, List<Path>> classifiedDependencies = new HashMap<>();
    classifiedDependencies.put(LayerType.DEPENDENCIES, new ArrayList<>());
    classifiedDependencies.put(LayerType.SNAPSHOT_DEPENDENCIES, new ArrayList<>());
    classifiedDependencies.put(LayerType.PROJECT_DEPENDENCIES, new ArrayList<>());

    for (Artifact artifact : dependencies) {
      if (projectArtifacts.contains(artifact)) {
        classifiedDependencies.get(LayerType.PROJECT_DEPENDENCIES).add(artifact.getFile().toPath());
      } else if (artifact.isSnapshot()) {
        classifiedDependencies
            .get(LayerType.SNAPSHOT_DEPENDENCIES)
            .add(artifact.getFile().toPath());
      } else {
        classifiedDependencies.get(LayerType.DEPENDENCIES).add(artifact.getFile().toPath());
      }
    }
    return classifiedDependencies;
  }

  @Override
  public List<Path> getClassFiles() throws IOException {
    return new DirectoryWalker(Paths.get(project.getBuild().getOutputDirectory())).walk().asList();
  }

  @Override
  public void waitForLoggingThread() {
    singleThreadedExecutor.shutDownAndAwaitTermination(LOGGING_THREAD_SHUTDOWN_TIMEOUT);
  }

  @Override
  public void configureEventHandlers(Containerizer containerizer) {
    containerizer
        .addEventHandler(LogEvent.class, this::log)
        .addEventHandler(
            TimerEvent.class, new TimerEventHandler(message -> log(LogEvent.debug(message))))
        .addEventHandler(
            ProgressEvent.class,
            new ProgressEventHandler(
                update ->
                    consoleLogger.setFooter(
                        ProgressDisplayGenerator.generateProgressDisplay(
                            update.getProgress(), update.getUnfinishedLeafTasks()))));
  }

  @Override
  public void log(LogEvent logEvent) {
    consoleLogger.log(logEvent.getLevel(), logEvent.getMessage());
  }

  @Override
  public String getToolName() {
    return TOOL_NAME;
  }

  @Override
  public String getToolVersion() {
    return jibPluginDescriptor.getVersion();
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
      return getChildValue(
              (Xpp3Dom) mavenJarPlugin.getConfiguration(), "archive", "manifest", "mainClass")
          .orElse(null);
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

  /**
   * Gets whether or not the given project is a war project. This is the case for projects with
   * packaging {@code war} and {@code gwt-app}.
   *
   * @return {@code true} if the project is a war project, {@code false} if not
   */
  @Override
  public boolean isWarProject() {
    String packaging = project.getPackaging();
    return "war".equals(packaging) || "gwt-app".equals(packaging);
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
      Optional<String> target = getChildValue(pluginConfiguration, "target");
      if (target.isPresent()) {
        return getVersionFromString(target.get());
      }
      Optional<String> release = getChildValue(pluginConfiguration, "release");
      if (release.isPresent()) {
        return getVersionFromString(release.get());
      }
    }
    return 6; // maven-compiler-plugin default is 1.6
  }

  @Override
  public boolean isOffline() {
    return session.isOffline();
  }

  @VisibleForTesting
  Path getWarArtifact() {
    Build build = project.getBuild();
    String warName = build.getFinalName();

    Plugin warPlugin = project.getPlugin("org.apache.maven.plugins:maven-war-plugin");
    if (warPlugin != null) {
      for (PluginExecution execution : warPlugin.getExecutions()) {
        if ("default-war".equals(execution.getId())) {
          Xpp3Dom configuration = (Xpp3Dom) execution.getConfiguration();
          warName = getChildValue(configuration, "warName").orElse(warName);
        }
      }
    }

    return Paths.get(build.getDirectory(), warName + ".war");
  }

  /**
   * Gets the path of the JAR that the Maven JAR Plugin generates. Will also make copies of jar
   * files with non-conforming names like those produced by springboot -- myjar.jar.original ->
   * myjar.original.jar.
   *
   * <p>https://maven.apache.org/plugins/maven-jar-plugin/jar-mojo.html
   * https://github.com/apache/maven-jar-plugin/blob/80f58a84aacff6e671f5a601d62a3a3800b507dc/src/main/java/org/apache/maven/plugins/jar/AbstractJarMojo.java#L177
   *
   * @return the path of the JAR
   * @throws IOException if copying jars with non-conforming names fails
   */
  @VisibleForTesting
  Path getJarArtifact() throws IOException {
    String classifier = null;
    Path buildDirectory = Paths.get(project.getBuild().getDirectory());
    Path outputDirectory = buildDirectory;

    // Read <classifier> and <outputDirectory> from maven-jar-plugin.
    Plugin jarPlugin = project.getPlugin("org.apache.maven.plugins:maven-jar-plugin");
    if (jarPlugin != null) {
      for (PluginExecution execution : jarPlugin.getExecutions()) {
        if ("default-jar".equals(execution.getId())) {
          Xpp3Dom configuration = (Xpp3Dom) execution.getConfiguration();
          classifier = getChildValue(configuration, "classifier").orElse(null);
          Optional<String> directoryString = getChildValue(configuration, "outputDirectory");

          if (directoryString.isPresent()) {
            outputDirectory = project.getBasedir().toPath().resolve(directoryString.get());
          }
        }
      }
    }

    String suffix = ".jar";
    if (jarRepackagedBySpringBoot()) {
      log(LogEvent.lifecycle("Spring Boot repackaging (fat JAR) detected; using the original JAR"));
      if (outputDirectory.equals(buildDirectory)) { // Spring renames original only when needed
        suffix += ".original";
      }
    }

    String noSuffixJarName =
        project.getBuild().getFinalName() + (classifier == null ? "" : '-' + classifier);
    Path jarPath = outputDirectory.resolve(noSuffixJarName + suffix);
    log(LogEvent.debug("Using JAR: " + jarPath));

    if (".jar".equals(suffix)) {
      return jarPath;
    }

    // "*" in "java -cp *" doesn't work if JAR doesn't end with ".jar". Copy the JAR with a new name
    // ending with ".jar".
    Path tempDirectory = tempDirectoryProvider.newDirectory();
    Path newJarPath = tempDirectory.resolve(noSuffixJarName + ".original.jar");
    Files.copy(jarPath, newJarPath);
    return newJarPath;
  }

  @VisibleForTesting
  boolean jarRepackagedBySpringBoot() {
    Plugin springBootPlugin =
        project.getPlugin("org.springframework.boot:spring-boot-maven-plugin");
    if (springBootPlugin != null) {
      for (PluginExecution execution : springBootPlugin.getExecutions()) {
        if (execution.getGoals().contains("repackage")) {
          Optional<String> skip = getChildValue((Xpp3Dom) execution.getConfiguration(), "skip");
          boolean skipped = "true".equals(skip.orElse("false"));
          return !skipped;
        }
      }
    }
    return false;
  }

  @Override
  public JibContainerBuilder runPluginExtensions(
      List<? extends ExtensionConfiguration> extensionConfigs,
      JibContainerBuilder jibContainerBuilder)
      throws JibPluginExtensionException {
    List<JibMavenPluginExtension> services =
        Lists.newArrayList(ServiceLoader.load(JibMavenPluginExtension.class).iterator());
    return runPluginExtensions(services, extensionConfigs, jibContainerBuilder);
  }

  @VisibleForTesting
  JibContainerBuilder runPluginExtensions(
      List<JibMavenPluginExtension> services,
      List<? extends ExtensionConfiguration> extensionConfigs,
      JibContainerBuilder jibContainerBuilder)
      throws JibPluginExtensionException {
    if (extensionConfigs.isEmpty()) {
      log(LogEvent.debug("No Jib plugin extensions configured to load"));
      return jibContainerBuilder;
    }

    JibMavenPluginExtension extension = null;
    ContainerBuildPlan buildPlan = jibContainerBuilder.toContainerBuildPlan();
    MavenExtensionData mavenData = new MavenExtensionData(project, session);
    MavenExtensionLogger extensionLogger = new MavenExtensionLogger(this::log);
    try {
      for (ExtensionConfiguration config : extensionConfigs) {
        String extensionClass = config.getExtensionClass();
        extension = findConfiguredExtension(services, extensionClass);
        if (extension == null) {
          throw new JibPluginExtensionException(
              JibMavenPluginExtension.class,
              "extension configured but not discovered on Jib runtime classpath: "
                  + extensionClass);
        }

        log(LogEvent.lifecycle("Running extension: " + extensionClass));
        buildPlan =
            extension.extendContainerBuildPlan(
                buildPlan, config.getProperties(), mavenData, extensionLogger);
        ImageReference.parse(buildPlan.getBaseImage()); // to validate image reference
      }
      return jibContainerBuilder.applyContainerBuildPlan(buildPlan);

    } catch (InvalidImageReferenceException ex) {
      throw new JibPluginExtensionException(
          Verify.verifyNotNull(extension).getClass(),
          "invalid base image reference: " + buildPlan.getBaseImage(),
          ex);
    }
  }

  @Nullable
  private JibMavenPluginExtension findConfiguredExtension(
      List<JibMavenPluginExtension> extensions, String extensionClass) {
    Predicate<JibMavenPluginExtension> matchesClassName =
        extension -> extension.getClass().getName().equals(extensionClass);
    return extensions.stream().filter(matchesClassName).findFirst().orElse(null);
  }
}
