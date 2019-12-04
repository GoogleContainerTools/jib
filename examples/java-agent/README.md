This SparkJava-based example builds a container image that includes the [Stackdriver Debugger Java Agent](https://cloud.google.com/debugger/docs/).
This project assumes the resulting image will be run inside Google Cloud Platform.

To build the image:

1. Replace `REPLACE-WITH-YOUR-GCP-PROJECT` with your GCP project in `pom.xml` or `build.gradle`.

1. Run `mvn package` or `./gradlew` to build the image.

SparkJava listens on port 4567 by default.

## Build and run on Google Cloud

[![Run on Google Cloud](https://deploy.cloud.run/button.svg)](https://deploy.cloud.run?git_repo=https://github.com/GoogleContainerTools/jib.git&dir=examples/java-agent)
