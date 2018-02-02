#!/bin/bash

# Fail on any error.
set -e

CREDENTIALS="$SONATYPE_USERNAME:$SONATYPE_PASSWORD"

# Display commands to stderr.
set -x

# Goes to the GCS directory.
cd $KOKORO_GFILE_DIR

# Finds the latest ubuntu/release-sign build directory.
LAST_BUILD=$(ls prod/jib/maven/ubuntu/release-sign/ | sort -rV | head -1)

# Finds the bundled jar file in the latest signed artifact directory.
BUNDLED_JAR_FILE=$(find `pwd`/prod/jib/maven/ubuntu/release-sign/${LAST_BUILD}/* -type f \( -iname \*-bundle.jar \))

# Usage: GetSessionID <variable name>
# Uses the environment variable CREDENTIALS.
# Stores the Nexus session ID in the given variable.
GetSessionID() {
	local __resultvar=$1

	# Makes a temporary file to store the login cookies.
	local cookies_temp=$(mktemp /tmp/sonatype_cookies.XXXXXXX)

	# Sends a login request.
	{ local login_response=$(curl 'https://oss.sonatype.org/service/local/authentication/login' -X 'GET' -u "$CREDENTIALS" -c $cookies_temp 2> /dev/null); } 2> /dev/null

	# Checks if login was successful.
	echo $login_response | grep -q '<loggedIn>true</loggedIn>'

	local login_check=$(echo -n $?)

	if [ "$login_check" -eq "1" ]; then
		return 1
	fi

	# Extracts the session ID from the cookies.
	local nxsessionid
	local nxsessionid_line=$(cat $cookies_temp | grep 'NXSESSIONID')
	nxsessionid=$(echo -n $nxsessionid_line | awk '{print $NF}')

	eval $__resultvar="'$nxsessionid'"
}

# Usage: UploadJAR <session ID> <file>
# Uploads the bundled JAR file to the Nexus Staging Repository.
UploadJAR() {
	curl 'https://oss.sonatype.org/service/local/staging/bundle_upload' -H "Cookie: NXSESSIONID=$1" -H 'Content-Type: multipart/form-data' --compressed -F "file=@$2"
}

# Gets the session ID.
GetSessionID NXSESSIONID
if [ $? -eq 1 ]; then
	echo 'Login failed!'
	exit 1
fi
echo 'Login successful.'

# Uploads the bundled JAR file.
echo 'Uploading artifact...'
UploadJAR $NXSESSIONID $BUNDLED_JAR_FILE
