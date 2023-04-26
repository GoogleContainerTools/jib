#!/bin/bash

export DOCKER_IP_UBUNTU="$(/sbin/ip route|awk '/default/ { print $3 }')"
echo "DOCKER_IP_UBUNTU: ${DOCKER_IP_UBUNTU}"
echo "${DOCKER_IP_UBUNTU} localhost" >> /etc/hosts
mkdir -p /tmpfs/auth
docker run --entrypoint htpasswd httpd:2 -Bbn testuser testpassword > /tmpfs/auth/htpasswd