package com.google.cloud.tools.jib.plugins.common;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public interface RawConfigurations {

  @Nullable
  String getFromImage();

  AuthProperty getFromAuth();

  @Nullable
  String getFromCredHelper();

  @Nullable
  List<String> getEntrypoint();

  @Nullable
  List<String> getProgramArguments();

  Map<String, String> getEnvironment();

  List<String> getPorts();

  @Nullable
  String getUser();

  boolean getUseCurrentTimestamp();

  List<String> getJvmFlags();

  @Nullable
  String getMainClass();

  @Nullable
  String getAppRoot();

  @Nullable
  AuthProperty getInferredAuth(String authTarget);
}
