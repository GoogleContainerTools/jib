package com.google.cloud.tools.jib.cache;

import com.google.cloud.tools.jib.json.JsonTemplate;

class MetadataEntryTemplate implements JsonTemplate {

  private String manfiest;
  private String containerConfig;

  String getManfiest() {
    return manfiest;
  }

  String getContainerConfig() {
    return containerConfig;
  }
}
