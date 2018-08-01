@echo on

REM Java 9 does not work with Mockito mockmaker.
set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%JAVA_HOME%\bin;%PATH%

cd github/jib

REM Stops any left-over containers.
FOR /f "tokens=*" %%i IN ('docker ps -q') DO docker rm -vf %%i

cd jib-core && call gradlew.bat clean build integrationTest --info --stacktrace && ^
cd ../jib-maven-plugin && call mvnw.cmd clean install -Pintegration-tests -B -U -X && ^
cd ../jib-gradle-plugin && call gradlew.bat clean build integrationTest --info --stacktrace

exit /b %ERRORLEVEL%
