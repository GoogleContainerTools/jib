@echo on

set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%JAVA_HOME%\bin;%PATH%

cd github/jib

cd jib-core && call gradlew.bat clean build publishToMavenLocal --info
cd ../jib-maven-plugin && call mvnw.cmd clean install cobertura:cobertura -B -U -X

exit /b %ERRORLEVEL%
