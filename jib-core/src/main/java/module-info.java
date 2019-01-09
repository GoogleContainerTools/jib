module com.google.cloud.tools.jib {
  exports com.google.cloud.tools.jib.api;

  requires java.logging;
  requires com.fasterxml.jackson.databind;
  requires com.google.api.client;
  requires com.google.common;
  requires org.apache.commons.compress;
  requires javassist;
}
