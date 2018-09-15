package com.google.cloud.tools.jib.gradle;

import java.io.File;
import javax.annotation.Nullable;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Sync;

public class ExplodedWarTask extends Sync {
  @Nullable private File explodedWarDirectory;

  public void setWarFile(File warFile) {
    from(getProject().zipTree(warFile));
  }

  /**
   * Sets the output directory of Sync Task
   *
   * @param explodedWarDirectory the directory where to extract the war file
   */
  public void setExplodedWarDirectory(File explodedWarDirectory) {
    this.explodedWarDirectory = explodedWarDirectory;
    into(explodedWarDirectory);
  }

  @OutputDirectory
  @Nullable
  public File getExplodedWarDirectory() {
    return explodedWarDirectory;
  }
}
