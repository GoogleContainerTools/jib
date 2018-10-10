Builds a container image that includes the [Stackdriver Debugger Java Agent](https://cloud.google.com/debugger/docs/).

To build the image:

1. In `pom.xml`, replace `REPLACE-WITH-YOUR-GCP-PROJECT` with your GCP project.

1. If running outside of Google Cloud Platform, you must:
     1. [Create and download service account credentials](https://cloud.google.com/debugger/docs/setup/java#local).
     2. Copy the credentials file into the jib `extraDirectory` location so that it is copied into the container.
     3. Configure the Stackdriver Debugger Java Agent to the location of the credentials file in the container.

1. Run `mvn compile jib:build`.

[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/examples/java-agent)](https://github.com/igrigorik/ga-beacon)
