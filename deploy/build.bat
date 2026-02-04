@echo off
chcp 65001
setlocal enabledelayedexpansion

echo 构建和拷贝脚本开始执行...

:: 设置目录路径
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"
for %%i in ("%SCRIPT_DIR%..") do set ROOT_DIR=%%~fi
set BACKEND_DIR=%ROOT_DIR%\backend
set BACKEND_GO_DIR=%ROOT_DIR%\backend-go
set FRONTEND_DIR=%ROOT_DIR%\frontend
set DEPLOY_DIR=%ROOT_DIR%\deploy
set BACKEND_TARGET_DIR=%BACKEND_DIR%\target
set BACKEND_GO_TARGET_DIR=%BACKEND_GO_DIR%\target
set FRONTEND_DIST_DIR=%FRONTEND_DIR%\dist
set BACKEND_DEPLOY_DIR=%DEPLOY_DIR%\backend
set BACKEND_GO_DEPLOY_DIR=%DEPLOY_DIR%\backend-go
set FRONTEND_DEPLOY_DIR=%DEPLOY_DIR%\frontend\html

echo 当前脚本目录: %SCRIPT_DIR%
echo 项目根目录: %ROOT_DIR%
echo 后端目录: %BACKEND_DIR%
echo Go 后端目录: %BACKEND_GO_DIR%
echo 前端目录: %FRONTEND_DIR%
echo 交付物目录: %DEPLOY_DIR%

:: 打包后端
echo.
echo 打包后端...
cd /d "%BACKEND_DIR%"
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
echo 错误：后端打包失败
exit /b 1
)

:: 打包 go 后端
echo.
echo 打包 go 后端...
cd /d "%BACKEND_GO_DIR%"
set GOOS=linux
set GOARCH=amd64
set CGO_ENABLED=0
go build -ldflags="-s -w" -o target/backend-go cmd/main.go
if %errorlevel% neq 0 (
echo 错误：后端打包失败
exit /b 1
)

:: 打包前端
echo.
echo 打包前端...
cd /d "%FRONTEND_DIR%"
call pnpm build
if %errorlevel% neq 0 (
echo 错误：前端打包失败
exit /b 1
)

:: 拷贝交付物
echo.
echo 拷贝交付物...

:: 清理旧目录
if exist "%BACKEND_DEPLOY_DIR%\app" (
echo 删除旧的后端app目录...
rd /s /q "%BACKEND_DEPLOY_DIR%\app"
)

if exist "%BACKEND_GO_DEPLOY_DIR%" (
echo 删除旧的 Go 后端目录...
rd /s /q "%BACKEND_GO_DEPLOY_DIR%"
)

if exist "%FRONTEND_DEPLOY_DIR%" (
echo 删除旧的前端html目录...
rd /s /q "%FRONTEND_DEPLOY_DIR%"
)

:: 拷贝 jar
if exist "%BACKEND_TARGET_DIR%" (
echo 拷贝后端文件...
for %%f in ("%BACKEND_TARGET_DIR%\*.jar") do (
echo 拷贝 %%~nxf 到 %BACKEND_DEPLOY_DIR%\app.jar
copy "%%f" "%BACKEND_DEPLOY_DIR%\app.jar" >nul
)
:: 执行 jar 展开
echo 展开 jar，分离 lib
call java -Djarmode=tools -jar "%BACKEND_DEPLOY_DIR%\app.jar" extract --destination "%BACKEND_DEPLOY_DIR%\app"
)

:: 拷贝后端 Go 文件
if exist "%BACKEND_GO_TARGET_DIR%" (
echo 拷贝后端 Go 文件...
xcopy "%BACKEND_GO_TARGET_DIR%" "%BACKEND_GO_DEPLOY_DIR%\" /E /I /H /Y >nul
xcopy "%BACKEND_GO_DIR%\config\config.yaml" "%BACKEND_GO_DEPLOY_DIR%\config\"  /E /I /H /Y>nul
)

:: 拷贝前端文件
if exist "%FRONTEND_DIST_DIR%" (
echo 拷贝前端文件...
xcopy "%FRONTEND_DIST_DIR%" "%FRONTEND_DEPLOY_DIR%\" /E /I /H /Y >nul
)

echo.
echo 构建和拷贝完成！

pause
