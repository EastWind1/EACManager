chcp 65001
@echo off
SET DEPLOY_DIR=%~dp0%

cd /d "%DEPLOY_DIR%front\nginx"
start "" .\nginx.exe

cd /d "%DEPLOY_DIR%backend"
start "Java App" cmd /c  java -jar -Dfile.encoding=UTF-8 -Dloader.path=.\lib app.jar
