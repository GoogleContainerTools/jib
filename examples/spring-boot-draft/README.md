SpringBoot Demo for using Jib

Tips: this is just a draft, please contribute if you have good suggestion.

### Quickstart

1.Modify configuration:

Modify `CUSTOM_REGISTRY_URL`, `YOUR_USER_NAME`, `YOUR_PASSWORD`, `PASSWORD_ENCRYPT_BY_MAVEN` as yourself, and change `credHelper` to match your platform .

2.Build the image:

Run `gradle jib` or `mvn compile jib:build` under project dictionary .

3.Run container:

Run `docker run --name hellojib -p 8080:8080 YOUR_USER_NAME/hellojib:jib` .

4.Access application:

Run `curl localhost:8080` and it will return `"Hello Jib"`, run container success .

### Custom base image and push registry

You will need to add custom configuration like below, configuration reference here for [Gradle](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#extended-usage) or  [Maven](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#extended-usage):

- Gradle

```gradle
jib {
    from {
        image = 'registry.hub.docker.com/openjdk:8-jdk-alpine'
    }
    to {
        image = 'CUSTOM_REGISTRY_URL/YOUR_USER_NAME/hellojib:jib'
        auth {
            username = 'YOUR_USERNAME'
            password = 'YOUR_PASSWORD'
        }
    }
    container {
        jvmFlags = ['-Djava.security.egd=file:/dev/./urandom', '-Duser.timezone=GMT+08', '-Xdebug']
        mainClass = 'com.example.jib.JibApplication'
        args = ['some args']
        ports = ['8080']
    }
}
```

- Maven 

pom.xml
 
```xml
    <build>
        <plugins>
            <plugin>
                <groupId>com.google.cloud.tools</groupId>
                <artifactId>jib-maven-plugin</artifactId>
                <version>${jib-maven-plugin.version}</version>
                <configuration>
                    <from>
                        <image>registry.hub.docker.com/openjdk:8-jdk-alpine</image>
                        <credHelper>osxkeychain</credHelper>
                    </from>
                    <to>
                        <image>CUSTOM_REGISTRY_URL/YOUR_USER_NAME/hellojib:jib</image>
                        <credHelper>osxkeychain</credHelper>
                    </to>
                    <container>
                        <jvmFlags>
                            <jvmFlag>-Djava.security.egd=file:/dev/./urandom</jvmFlag>
                            <jvmFlag>-Xdebug</jvmFlag>
                            <jvmFlag>-Duser.timezone=GMT+08</jvmFlag>
                        </jvmFlags>
                        <mainClass>com.example.jib.JibApplication</mainClass>
                        <args>
                            <arg>some args</arg>
                        </args>
                        <ports>
                            <port>8080</port>
                        </ports>
                        <format>OCI</format>
                    </container>
                </configuration>
            </plugin>
        </plugins>
    </build>
```

settings.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                          https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>CUSTOM_REGISTRY_URL</id>
            <username>YOUR_USER_NAME</username>
            <password>PASSWORD_ENCRYPT_BY_MAVEN</password>
        </server>
    </servers>
</settings>
```