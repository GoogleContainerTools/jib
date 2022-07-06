#!/bin/bash

docker-machine ls
docker-machine start default
export DOCKER_IP="$(docker-machine ip default)"
echo $DOCKER_IP

docker-machine ssh default "echo '{ \"insecure-registries\":[\"$DOCKER_IP:5000\", \"$DOCKER_IP:8080\"] }' | sudo tee /etc/docker/daemon.json "
docker-machine ssh default "echo 'DOCKER_OPTS=\"--config-file=/etc/docker/daemon.json\"' | sudo tee -a /var/lib/boot2docker/profile "
docker-machine ssh default "mkdir /home/docker/auth; docker run --entrypoint htpasswd httpd:2 -Bbn testuser testpassword > /home/docker/auth/htpasswd"
docker-machine ssh default "cat /home/docker/auth/htpasswd"
docker-machine ssh default "sudo /etc/init.d/docker restart"
docker-machine inspect default
docker-machine env default
eval "$(docker-machine env default)"

export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-8-latest/Contents/Home"
export PATH=$JAVA_HOME/bin:$PATH
echo $JAVA_HOME