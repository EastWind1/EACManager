chcp 65001
@echo off
SET DEPLOY_DIR=%~dp0%

taskkill /im nginx.exe /f

tasklist | findstr "java" | findstr "app.jar" >nul && (
    for /f "tokens=2 delims=," %%a in ('tasklist ^| findstr "java" ^| findstr "app.jar"') do taskkill /pid %%a /f
) || echo  未找到 Java 进程

