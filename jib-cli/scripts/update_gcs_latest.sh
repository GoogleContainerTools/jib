#!/bin/bash -
# Usage: ./jib-cli/scripts/update_gcs_latest.sh <release version>

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

[ $# -ne 1 ] && Die "Usage: ./jib-cli/scripts/update_gcs_latest.sh <release version>"

CheckVersion $1

versionString="{\"latest\":\"$1\"}"
destination="gs://jib-versions/jib-cli"

echo $versionString > jib-cli-temp
gsutil cp jib-cli-temp $destination
gsutil acl ch -u allUsers:READ $destination
rm jib-cli-temp

gcsResult=$(curl https://storage.googleapis.com/jib-versions/jib-cli)
if [ "$gcsResult" == "$versionString" ]
then
  EchoGreen "Version updated successfully"
else
  Die "Version update failed"
fi