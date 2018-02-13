@echo on

REM Java 9 does not work with Mockito mockmaker.
set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%JAVA_HOME%\bin;%PATH%

cd github/jib

REM Stops any left-over containers.
call docker stop ('call docker container ls --quiet')

cd jib-core && call gradlew.bat clean build integrationTest publishToMavenLocal --info && ^
cd ../jib-maven-plugin && call mvnw.cmd clean install cobertura:cobertura -B -U -X

exit /b %ERRORLEVEL%
