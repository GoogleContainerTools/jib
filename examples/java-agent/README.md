This SparkJava-based example builds a container image that includes the [Stackdriver Debugger Java Agent](https://cloud.google.com/debugger/docs/).
This project assumes the resulting image will be run inside Google CLoud Platform. To run outside of
GCP, follow the Stackdrive Debugger instructions to [create and download service account
credentials](https://cloud.google.com/debugger/docs/setup/java#local). 

To build the image:

1. Replace `REPLACE-WITH-YOUR-GCP-PROJECT` with your GCP project in `pom.xml` or `build.gradle`.

1. Run `mvn package` or `./gradlew` to build the image.

SparkJava listens on port 4567 by default.

[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/examples/java-agent)](https://github.com/igrigorik/ga-beacon)
