@echo on

REM Java 9 does not work with Mockito mockmaker.
echo %JAVA_HOME%

cd github/jib

REM Stops any left-over containers.
REM FOR /f "tokens=*" %%i IN ('docker ps -aq') DO docker rm -vf %%i

REM Sets the integration testing project.
set JIB_INTEGRATION_TESTING_PROJECT=jib-integration-testing

REM TODO: Enable integration tests once docker works (b/73345382).
gradlew.bat clean build --info --stacktrace

exit /b %ERRORLEVEL%
