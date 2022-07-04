package com.google.cloud.tools.jib.docker;

import com.google.cloud.tools.jib.api.DockerClient;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.image.ImageTarball;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

public class AnotherDockerClient implements DockerClient {
  @Override
  public boolean supported(Map<String, String> parameters) {
    return parameters.containsKey("test");
  }

  @Override
  public String load(ImageTarball imageTarball, Consumer<Long> writtenByteCountListener)
      throws InterruptedException, IOException {
    return null;
  }

  @Override
  public void save(
      ImageReference imageReference, Path outputPath, Consumer<Long> writtenByteCountListener)
      throws InterruptedException, IOException {}

  @Override
  public DockerImageDetails inspect(ImageReference imageReference)
      throws IOException, InterruptedException {
    return null;
  }
}
