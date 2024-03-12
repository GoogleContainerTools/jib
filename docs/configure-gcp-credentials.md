# Configuring Credentials for [Google Container Registry (GCR)](https://cloud.google.com/container-registry/)

There are a few ways of supplying Jib with the credentials to push and pull images from your private GCR registry.

## Using the Docker credential helper

The easiest way is to install the [docker-credential-gcr](https://github.com/GoogleCloudPlatform/docker-credential-gcr).

### Installation

If you have [`gcloud` (Cloud SDK)](https://cloud.google.com/sdk/gcloud/) installed, you can run:

```shell
gcloud components install docker-credential-gcr
```

Alternatively, if you have `go get` installed, you can run:

```shell
go get -u github.com/GoogleCloudPlatform/docker-credential-gcr
```

Alternatively, you can download `docker-credential-gcr` from its [Github Releases](https://github.com/GoogleCloudPlatform/docker-credential-gcr/releases).

### Enable the Container Registry API

If you have not already done so, make sure you [enable the Container Registry API](https://console.cloud.google.com/flows/enableapi?apiid=containerregistry.googleapis.com&redirect=https://github.com/GoogleContainerRegistry/jib) for the Google Cloud Platform account you wish to use.

### Log in

Log in to the account you with to use with:

```shell
docker-credential-gcr gcr-login
```

This stores the credentials in `docker-credential-gcr`'s private credential store.

Now, you can use Jib to pull and push from images in the form `gcr.io/your-gcp-project/your-image-name`.
