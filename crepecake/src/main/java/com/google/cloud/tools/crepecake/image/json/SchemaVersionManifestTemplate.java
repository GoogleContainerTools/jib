package com.google.cloud.tools.crepecake.image.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.tools.crepecake.json.JsonTemplate;

/** JSON template for just the {@code schemaVersion} field of image manifests. A {@code schemaVersion} of {@code 1} indicates that the JSON should be parsed as {@link V21ManifestTemplate}, whereas a {@code schemaVersion] of {@code 2} indicates that the JSON should be parsed as {@link V22ManifestTemplate}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaVersionManifestTemplate extends JsonTemplate {

  private int schemaVersion;

  public int getSchemaVersion() {
    return schemaVersion;
  }
}
