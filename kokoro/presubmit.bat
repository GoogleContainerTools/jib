@echo on

REM Java 9 does not work with Mockito mockmaker.
set JAVA_HOME=c:\program files\java\jdk1.8.0_152
set PATH=%JAVA_HOME%\bin;%PATH%

cd github/jib

REM Stops any left-over containers.
REM FOR /f "tokens=*" %%i IN ('docker ps -aq') DO docker rm -vf %%i

cd jib-core && call gradlew.bat clean build --info --stacktrace && ^
cd ../jib-plugins-common && call gradlew.bat clean build --info --stacktrace && ^
cd ../jib-maven-plugin && call mvnw.cmd clean install -B -U -e && ^
cd ../jib-gradle-plugin && call gradlew.bat clean build --info --stacktrace

exit /b %ERRORLEVEL%
