@echo on

cd github/jib

cd crepecake && call gradlew.bat clean build --info
exit /b %ERRORLEVEL%
