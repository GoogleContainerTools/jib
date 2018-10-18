package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.plugins.common.AuthProperty;
import com.google.cloud.tools.jib.plugins.common.RawConfigurations;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class GradleRawConfigurations implements RawConfigurations {

  private final JibExtension jibExtension;

  public GradleRawConfigurations(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }

  @Nullable
  @Override
  public String getFromImage() {
    return jibExtension.getFrom().getImage();
  }

  @Override
  public AuthProperty getFromAuth() {
    return jibExtension.getFrom().getAuth();
  }

  @Nullable
  @Override
  public String getFromCredHelper() {
    return jibExtension.getFrom().getCredHelper();
  }

  @Nullable
  @Override
  public List<String> getEntrypoint() {
    return jibExtension.getContainer().getEntrypoint();
  }

  @Nullable
  @Override
  public List<String> getProgramArguments() {
    return jibExtension.getContainer().getArgs();
  }

  @Override
  public Map<String, String> getEnvironment() {
    return jibExtension.getContainer().getEnvironment();
  }

  @Override
  public List<String> getPorts() {
    return jibExtension.getContainer().getPorts();
  }

  @Nullable
  @Override
  public String getUser() {
    return jibExtension.getContainer().getUser();
  }

  @Override
  public boolean getUseCurrentTimestamp() {
    return jibExtension.getContainer().getUseCurrentTimestamp();
  }

  @Override
  public List<String> getJvmFlags() {
    return jibExtension.getContainer().getJvmFlags();
  }

  @Nullable
  @Override
  public String getMainClass() {
    return jibExtension.getContainer().getMainClass();
  }

  @Nullable
  @Override
  public String getAppRoot() {
    return jibExtension.getContainer().getAppRoot();
  }

  @Nullable
  @Override
  public AuthProperty getInferredAuth(String authTarget) {
    return null;
  }
}
