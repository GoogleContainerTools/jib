@echo on

cd github/jib

cd jib-core && call gradlew.bat clean build --info
exit /b %ERRORLEVEL%
