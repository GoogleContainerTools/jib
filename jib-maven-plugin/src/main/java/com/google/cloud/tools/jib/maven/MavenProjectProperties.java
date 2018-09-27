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

import com.google.cloud.tools.jib.event.DefaultEventDispatcher;
import com.google.cloud.tools.jib.event.EventDispatcher;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.JibEventType;
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath;
import com.google.cloud.tools.jib.frontend.JavaLayerConfigurations;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.MainClassResolver;
import com.google.cloud.tools.jib.plugins.common.ProjectProperties;
import com.google.cloud.tools.jib.plugins.common.TimerEventHandler;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/** Obtains information about a {@link MavenProject}. */
public class MavenProjectProperties implements ProjectProperties {

  /** Used for logging during main class inference and analysis of user configuration. */
  public static final String PLUGIN_NAME = "jib-maven-plugin";

  /** Used to identify this plugin when interacting with the maven system. */
  public static final String PLUGIN_KEY = "com.google.cloud.tools:" + PLUGIN_NAME;

  /** Used to generate the User-Agent header and history metadata. */
  static final String TOOL_NAME = "jib-maven-plugin";

  /** Used for logging during main class inference. */
  private static final String JAR_PLUGIN_NAME = "'maven-jar-plugin'";

  /**
   * @param project the {@link MavenProject} for the plugin.
   * @param log the Maven {@link Log} to log messages during Jib execution
   * @param extraDirectory path to the directory for the extra files layer
   * @param appRoot root directory in the image where the app will be placed
   * @return a MavenProjectProperties from the given project and logger.
   * @throws MojoExecutionException if no class files are found in the output directory.
   */
  static MavenProjectProperties getForProject(
      MavenProject project, Log log, Path extraDirectory, AbsoluteUnixPath appRoot)
      throws MojoExecutionException {
    try {
      return new MavenProjectProperties(
          project,
          makeEventDispatcher(log),
          MavenLayerConfigurations.getForProject(project, extraDirectory, appRoot));

    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Obtaining project build output files failed; make sure you have compiled your project "
              + "before trying to build the image. (Did you accidentally run \"mvn clean "
              + "jib:build\" instead of \"mvn clean compile jib:build\"?)",
          ex);
    }
  }

  private static EventDispatcher makeEventDispatcher(Log log) {
    return new DefaultEventDispatcher(
        new EventHandlers()
            .add(JibEventType.LOGGING, new LogEventHandler(log))
            .add(JibEventType.TIMING, new TimerEventHandler(log::debug)));
  }

  private final MavenProject project;
  private final EventDispatcher eventDispatcher;
  private final JavaLayerConfigurations javaLayerConfigurations;

  @VisibleForTesting
  MavenProjectProperties(
      MavenProject project,
      EventDispatcher eventDispatcher,
      JavaLayerConfigurations javaLayerConfigurations) {
    this.project = project;
    this.eventDispatcher = eventDispatcher;
    this.javaLayerConfigurations = javaLayerConfigurations;
  }

  @Override
  public JavaLayerConfigurations getJavaLayerConfigurations() {
    return javaLayerConfigurations;
  }

  @Override
  public EventDispatcher getEventDispatcher() {
    return eventDispatcher;
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
  public Path getCacheDirectory() {
    return Paths.get(project.getBuild().getDirectory(), CACHE_DIRECTORY_NAME);
  }

  @Override
  public String getJarPluginName() {
    return JAR_PLUGIN_NAME;
  }

  @Override
  public boolean isWarProject() {
    return false; // TODO: to be implemented. For now, assume false.
  }

  /**
   * Tries to resolve the main class.
   *
   * @param jibPluginConfiguration the mojo configuration properties.
   * @return the configured main class, or the inferred main class if none is configured.
   * @throws MojoExecutionException if resolving the main class fails.
   */
  String getMainClass(JibPluginConfiguration jibPluginConfiguration) throws MojoExecutionException {
    try {
      return MainClassResolver.resolveMainClass(jibPluginConfiguration.getMainClass(), this);
    } catch (MainClassInferenceException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);
    }
  }
}
