package com.google.cloud.tools.jib.plugins.common;

import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtensionConfiguration;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import java.util.Optional;

public interface ExtensionConfigurationWithInjectedPlugin extends ExtensionConfiguration {
  Optional<? extends JibPluginExtension> getInjectedExtension();
}
