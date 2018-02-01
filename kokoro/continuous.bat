@echo on

cd github/jib

cd jib-core && call gradlew.bat clean build publishToMavenLocal --info
cd ../jib-maven-plugin && call mvnw.cmd clean install cobertura:cobertura -B -U -X

exit /b %ERRORLEVEL%
g