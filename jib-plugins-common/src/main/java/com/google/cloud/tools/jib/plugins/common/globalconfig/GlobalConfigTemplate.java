package com.google.cloud.tools.jib.plugins.common.globalconfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.jib.json.JsonTemplate;

/** JSON template for the global configuration file. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GlobalConfigTemplate implements JsonTemplate {

  private boolean disableUpdateCheck;

  public void setDisableUpdateCheck(boolean disableUpdateCheck) {
    this.disableUpdateCheck = disableUpdateCheck;
  }

  public boolean isDisableUpdateCheck() {
    return disableUpdateCheck;
  }
}
