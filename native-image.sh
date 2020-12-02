#!/bin/sh

# Do ./gradlew jib-cli:instDist before running this.

set -o errexit

cd jib-cli/build/install/jib

java -version

MAIN_CLASS=com.google.cloud.tools.jib.cli.cli2.JibCli
CLASSPATH=$( find lib/ -name '*.jar' -printf ':%p' | cut -c2- )
echo
echo "* CLASSPATH: ${CLASSPATH}"
echo "* Main class: ${MAIN_CLASS}"
echo

###############################################################################
# Auto-gen Picocli reflection configuration JSON
# (https://picocli.info/picocli-on-graalvm.html)
#
PICOCLI_JAR=../../picocli-4.5.2.jar
PICOCLI_CODEGEN_JAR=../../picocli-codegen-4.5.2.jar
if [ ! -e "${PICOCLI_JAR}" -o ! -e "${PICOCLI_CODEGEN_JAR}" ]; then
  curl -so "${PICOCLI_JAR}" https://repo1.maven.org/maven2/info/picocli/picocli/4.5.2/picocli-4.5.2.jar
  curl -so "${PICOCLI_CODEGEN_JAR}" https://repo1.maven.org/maven2/info/picocli/picocli-codegen/4.5.2/picocli-codegen-4.5.2.jar
fi

echo "* Generating Picocli reflection configuration JSON..."
java -cp "${CLASSPATH}:${PICOCLI_JAR}:${PICOCLI_CODEGEN_JAR}" \
  picocli.codegen.aot.graalvm.ReflectionConfigGenerator \
    "${MAIN_CLASS}" \
    > picocli-reflect.json

###############################################################################
# Generate a native image
#
echo "* Generating a native image..."
native-image --static --no-fallback --no-server \
  --native-image-info --verbose \
  --enable-https --enable-http \
  -H:ReflectionConfigurationFiles=picocli-reflect.json \
  -H:ConfigurationFileDirectories=../../../../jib-cli/graalvm11/ \
  -H:+ReportExceptionStackTraces \
  -H:+ReportUnsupportedElementsAtRuntime \
  -cp "${CLASSPATH}" \
  "${MAIN_CLASS}" bin/jib-native

#  -H:ReflectionConfigurationFiles=picocli-reflect.json,../../../../native-image-config/reflect-config.json \
#  -H:ResourceConfigurationFiles=../../../../native-image-config/resource-config.json \
#  -H:ReflectionConfigurationFiles=picocli-reflect.json,../../../graalvm/reflection.json \
#  -H:EnableURLProtocols=https,http \
#  -H:+AllowIncompleteClasspath \
