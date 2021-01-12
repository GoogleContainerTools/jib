package com.google.cloud.tools.jib.plugins.common.globalconfig;

import com.google.cloud.tools.jib.json.JsonTemplate;
import java.util.List;

public class RegistryMirrorTemplate implements JsonTemplate {

  private String registry;
  private List<String> mirrors;
}
