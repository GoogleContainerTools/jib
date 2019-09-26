Builds a container image that outputs `Hello World` when run.

To build the image:

1. In `pom.xml`, replace `REPLACE-WITH-YOUR-GCP-PROJECT` with your GCP project.

1. Run `mvn compile jib:build`.

## Build and run on Google Cloud

[![Run on Google Cloud](https://storage.googleapis.com/cloudrun/button.svg)](https://console.cloud.google.com/cloudshell/editor?shellonly=true&cloudshell_image=gcr.io/chanseok-playground-new/cloud-run-button&cloudshell_git_repo=https://github.com/GoogleContainerTools/jib.git&cloudshell_working_dir=examples/helloworld)
