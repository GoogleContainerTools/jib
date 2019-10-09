package com.google.cloud.tools.jib.plugins.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.api.LayerEntry;
import com.google.cloud.tools.jib.json.JsonTemplate;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a JSON string containing files and directories that <a
 * href="https://github.com/GoogleContainerTools/skaffold">Skaffold</a> can use for synchronizing
 * files against a remote container
 *
 * <p>Example:
 *
 * <pre>{@code
 * {
 *   "generated": [
 *      {
 *        src: "fileX-local"
 *        dest: "fileX-remote"
 *      },
 *      {
 *        src: "dirX-local"
 *        dest: "dirX-remote"
 *      }
 *   ],
 *   "direct": [
 *      {
 *        src: "fileY-local"
 *        dest: "fileY-remote"
 *      },
 *      {
 *        src: "dirY-local"
 *        dest: "dirY-remote"
 *      },
 *   ]
 * }
 * }</pre>
 */
public class SkaffoldSyncMapTemplate implements JsonTemplate {

  /**
   * A single entry in the skaffold sync map, may be eventually extended to support permissions and
   * ownership
   */
  public static class FileTemplate implements JsonTemplate {
    private final String src;
    private final String dest;

    @JsonCreator
    public FileTemplate(
        @JsonProperty(value = "src", required = true) String src,
        @JsonProperty(value = "dest", required = true) String dest) {
      this.src = src;
      this.dest = dest;
    }
  }

  private List<FileTemplate> generated = new ArrayList<>();
  private List<FileTemplate> direct = new ArrayList<>();

  public static SkaffoldSyncMapTemplate from(String jsonString) throws IOException {
    return new ObjectMapper().readValue(jsonString, SkaffoldSyncMapTemplate.class);
  }

  public void addGenerated(LayerEntry layerEntry) {
    generated.add(
        new FileTemplate(
            layerEntry.getSourceFile().toString(), layerEntry.getExtractionPath().toString()));
  }

  public void addDirect(LayerEntry layerEntry) {
    direct.add(
        new FileTemplate(
            layerEntry.getSourceFile().toString(), layerEntry.getExtractionPath().toString()));
  }

  public String getJsonString() throws IOException {
    try (OutputStream outputStream = new ByteArrayOutputStream()) {
      new ObjectMapper().writeValue(outputStream, this);
      return outputStream.toString();
    }
  }
}
