# Accessing private docker registry with self-signed certificate

Currently, `jib` does not support docker registries with self-signed `https` certificate.

Jib uses the JVM's list of approved CA Certificates to validate SSL certificates. The following instructions describe how to add a registry's self-signed certificate to the JVM's approved CAs.

The certificate will be trusted at the JRE level, affecting all Java applications running on it. You will also need to re-import the certificate when you use a different JRE or upgrade it.

## Using KeyStore Explorer

The easiest way to import the self-signed certificate into JVM is using the [KeyStore Explorer](http://keystore-explorer.org/).

KeyStore Explorer is an open source GUI replacement for the Java command-line utilities keytool and jarsigner. KeyStore Explorer presents their functionality, and more, via an intuitive graphical user interface.

### Installation

Download and install KeyStore Explorer from [official website](http://keystore-explorer.org/downloads.html)

### Indentify java runtime being used by build tool

#### Maven

Run `mvn --version` and take note on java runtime:

```shell
$ mvn --version
Apache Maven 3.5.4 (1edded0938998edf8bf061f1ceb3cfdeccf443fe; 2018-06-18T06:33:14+12:00)
Maven home: /usr/local/Cellar/maven/3.5.4/libexec
Java version: 1.8.0_172, vendor: Oracle Corporation, runtime: /Library/Java/JavaVirtualMachines/jdk1.8.0_172.jdk/Contents/Home/jre
Default locale: en_NZ, platform encoding: UTF-8
OS name: "mac os x", version: "10.13.6", arch: "x86_64", family: "mac"
```

On this example the `java runtime` is `/Library/Java/JavaVirtualMachines/jdk1.8.0_172.jdk/Contents/Home/jre`.

### Import certificate

* Launch `KeyStore Explorer`

#### 1. Select java runtime

* Open `Preferences`
* Go to `Authorithy Certificates`
* Replace `CA Certificates KeyStore` with the `java runtime` from the previous step. Include `lib/security/cacerts` on the path.

#### 2. Import certificate

* Hit `Open CA Certificates KeyStore` on the main window
* On the tool bar, click on `Import Trusted Certificate`
* Select the certifcate file on disk
* Give it a name, or use suggested name, hit `OK`
* Hit ok on the success window
* Last step, hit `Save`
