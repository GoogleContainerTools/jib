package com.google.cloud.tools.jib.api.buildplan;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.concurrent.Immutable;

@Immutable
public class PlatformDependentLayer implements LayerObject {

  public static class Builder {
    private String name = "";
    private Map<Platform, FileEntriesLayer> entries = new HashMap<>();

    private Builder() {}

    /**
     * Sets a name for this layer. This name does not affect the contents of the layer.
     *
     * @param name the name
     * @return this
     */
    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    public Builder setEntries(Map<Platform, FileEntriesLayer> entries) {
      this.entries = new HashMap<>(entries);
      return this;
    }

    public Builder addEntry(Platform platform, FileEntriesLayer layer) {
      entries.put(platform, layer);
      return this;
    }

    public PlatformDependentLayer build() {
      return new PlatformDependentLayer(name, entries);
    }
  }

  /**
   * Gets a new {@link FileEntriesLayer.Builder} for {@link FileEntriesLayer}.
   *
   * @return a new {@link FileEntriesLayer.Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  private final String name;
  private final Map<Platform, FileEntriesLayer> entries;

  private PlatformDependentLayer(String name, Map<Platform, FileEntriesLayer> entries) {
    this.name = name;
    this.entries = Collections.unmodifiableMap(new HashMap<>(entries));
  }

  @Override
  public Type getType() {
    return Type.PLATFORM_DEPENDENT;
  }

  @Override
  public String getName() {
    return name;
  }

  /**
   * Gets the map of entries.
   *
   * @return the map of entries
   */
  public Map<Platform, FileEntriesLayer> getEntries() {
    return entries;
  }

  /**
   * Creates a builder configured with the current values.
   *
   * @return {@link Builder} configured with the current values
   */
  public Builder toBuilder() {
    return builder().setName(name).setEntries(entries);
  }
}
