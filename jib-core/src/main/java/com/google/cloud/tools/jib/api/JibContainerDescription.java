package com.google.cloud.tools.jib.api;

import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A class containing the representation of the contents of a container. Currently only exposes
 * "layers", but can be extended to expose {@link ContainerConfiguration} or other informational
 * classes.
 *
 * <p>This class is immutable and thread-safe.
 */
public class JibContainerDescription {

  private final ImmutableList<LayerConfiguration> layers;

  JibContainerDescription(List<LayerConfiguration> layers) {
    this.layers = ImmutableList.copyOf(layers);
  }

  /** Returns a list of "user configured" layers, does *not* include base layer information. */
  public List<LayerConfiguration> getLayers() {
    return layers;
  }
}
