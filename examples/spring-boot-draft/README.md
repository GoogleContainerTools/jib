SpringBoot Demo for using Jib

## Tips: this is just a draft 

Please contribute if you have good suggestion/contribute if you have suggested edits .

### Quickstart

1. Login docker registry with password `hellojib`:
 
```
docker login -u hellojib
```

2. Modify configuration:

Modify  `credHelper` to match your platform .

3. Build the image:

Run `gradle jib` or `mvn compile jib:build` under project dictionary .

4. Run container:

Run `docker run --name hellojib -p 8080:8080 hellojib/hellojib:jib` .

5. Access application:

Run `curl localhost:8080` and it will return `"Hello Jib"` .

### Custom base image and push registry

You will need to add custom configuration (examples below). See the configuration reference for [Gradle](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin#extended-usage) or  [Maven](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#extended-usage) for more details:

- Gradle

build.gradle

```gradle
jib {
    from {
        image = 'registry.hub.docker.com/openjdk:8-jdk-alpine'
        credHelper = 'osxkeychain'
    }
    to {
        image = 'CUSTOM_REGISTRY_URL/YOUR_USER_NAME//hellojib:jib'
        credHelper = 'osxkeychain'
        // Or use auth like below if you are not login
        // Defined in gradle.properties
        //auth {
        //    username = dockerUsername
        //    password = dockerPassword
        //}
    }
    container {
        jvmFlags = ['-Djava.security.egd=file:/dev/./urandom', '-Duser.timezone=GMT+08', '-Xdebug']
        mainClass = 'com.example.jib.JibApplication'
        args = ['some args']
        ports = ['8080']
    }
}
```

gradle.properties

```gradle
dockerUsername=YOUR_USERNAME
dockerPassword=YOUR_PASSWORD
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
                    </container>
                </configuration>
            </plugin>
        </plugins>
    </build>
```

settings.xml(`${MAVEN_HOME}/conf/settings.xml`)(You should config this is you are not login)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                          https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>CUSTOM_REGISTRY_URL</id>
            <username>YOUR_USERNAME</username>
            <password>YOUR_PASSWORD</password>
        </server>
    </servers>
</settings>
```

[![Analytics](https://cloud-tools-for-java-metrics.appspot.com/UA-121724379-2/examples/spring-boot-draft)](https://github.com/igrigorik/ga-beacon)
