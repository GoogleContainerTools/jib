#!/bin/bash -
# Usage: ./jib-gradle-plugin/scripts/update_gcs_latest.sh <release version>

set -o errexit

EchoRed() {
	echo "$(tput setaf 1; tput bold)$1$(tput sgr0)"
}
EchoGreen() {
	echo "$(tput setaf 2; tput bold)$1$(tput sgr0)"
}

Die() {
	EchoRed "$1"
	exit 1
}

# Usage: CheckVersion <version>
CheckVersion() {
    [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+)?$ ]] || Die "Version: $1 not in ###.###.###[-XXX] format."
}

[ $# -ne 1 ] && Die "Usage: ./jib-gradle-plugin/scripts/update_gcs_latest.sh <release version>"

CheckVersion $1

versionString="{\"latest\":\"$1\"}"
destination="gs://jib-versions/jib-gradle"

echo $versionString > jib-gradle
gsutil cp jib-gradle $destination
gsutil acl ch -u allUsers:READ $destination
rm jib-gradle

gcsResult=$(curl https://storage.googleapis.com/jib-versions/jib-gradle)
if [ "$gcsResult" == "$versionString" ]
then
  EchoGreen "Version updated successfully"
else
  Die "Version update failed"
fi