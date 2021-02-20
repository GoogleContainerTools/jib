package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtensionConfiguration;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import java.util.Optional;

/** To be implemented if dependency injection of extensions is supported by the build system. */
public interface ExtensionConfigurationWithInjectedPlugin extends ExtensionConfiguration {
  /**
   * The matching extension, if it has been injected.
   *
   * @return the extension
   */
  Optional<? extends JibPluginExtension> getInjectedExtension();
}
