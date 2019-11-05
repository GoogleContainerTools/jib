package com.google.cloud.tools.jib.maven.skaffold;

import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.maven.JibPluginConfiguration;
import com.google.cloud.tools.jib.maven.MavenProjectProperties;
import com.google.cloud.tools.jib.maven.MavenRawConfiguration;
import com.google.cloud.tools.jib.maven.MojoCommon;
import com.google.cloud.tools.jib.plugins.common.ContainerizingMode;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerizingModeException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.common.annotations.VisibleForTesting;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
    name = SyncMapMojo.GOAL_NAME,
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class SyncMapMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "_skaffold-sync-map";

  @Override
  public void execute() throws MojoExecutionException {
    checkJibVersion();
    if (MojoCommon.shouldSkipJibExecution(this)) {
      return;
    }

    // add check that means this is only for jars
    if (!"jar".equals(getProject().getPackaging())) {
      throw new MojoExecutionException("Sync is currently only available for jar style projects");
    }
    // add check for exploded containerization
    try {
      if (!ContainerizingMode.EXPLODED.equals(ContainerizingMode.from(getContainerizingMode()))) {
        throw new MojoExecutionException("Sync is only available in exploded mode");
      }
    } catch (InvalidContainerizingModeException ex) {
      throw new MojoExecutionException("Invalid containerizing mode", ex);
    }

    try (TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider()) {
      MavenProjectProperties projectProperties =
          MavenProjectProperties.getForProject(
              getProject(), getSession(), getLog(), tempDirectoryProvider);

      MavenRawConfiguration configuration = new MavenRawConfiguration(this);

      try {
        String syncMapJson =
            PluginConfigurationProcessor.getSkaffoldSyncMap(configuration, projectProperties);

        System.out.println("\nBEGIN JIB JSON");
        System.out.println(syncMapJson);

      } catch (Exception e) {
        throw new MojoExecutionException("Failed to generate Jib file map", e);
      }
    }
  }
}
