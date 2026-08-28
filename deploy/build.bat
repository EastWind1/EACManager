@echo off
chcp 65001
setlocal enabledelayedexpansion

echo 构建和拷贝脚本开始执行...

:: 设置目录
for %%i in ("%~dp0..") do set ROOT_DIR=%%~fi
set OCR_DIR=%ROOT_DIR%\ocr-server
set BACKEND_JAVA_DIR=%ROOT_DIR%\backend-java
set BACKEND_GO_DIR=%ROOT_DIR%\backend-go
set FRONTEND_DIR=%ROOT_DIR%\frontend
set DEPLOY_DIR=%ROOT_DIR%\deploy
set DEPLOY_HTML=%DEPLOY_DIR%\frontend\html

:: 打包 Java 后端
echo [1/4] 打包 Java 后端...
cd /d "%BACKEND_JAVA_DIR%"
call mvn clean package -DskipTests || exit /b 1

:: 打包 Go 后端
echo [2/4] 打包 Go 后端...
cd /d "%BACKEND_GO_DIR%"
set GOOS=linux& set GOARCH=amd64& set CGO_ENABLED=0
go build -ldflags="-s -w" -o target/backend-go cmd/main.go || exit /b 1

:: 打包前端
echo [3/4] 打包前端...
cd /d "%FRONTEND_DIR%"
call pnpm build || exit /b 1

:: 清理旧文件
if exist "%DEPLOY_DIR%\backend-java" rd /s /q "%DEPLOY_DIR%\backend-java"
if exist "%DEPLOY_DIR%\backend-go" rd /s /q "%DEPLOY_DIR%\backend-go"
if exist "%DEPLOY_DIR%\ocr-server" rd /s /q "%DEPLOY_DIR%\ocr-server"
if exist "%DEPLOY_HTML%" rd /s /q "%DEPLOY_HTML%"

:: 拷贝 Java 交付物
mkdir "%DEPLOY_DIR%\backend-java"
for %%f in ("%BACKEND_JAVA_DIR%\target\*.jar") do (
    java -Djarmode=tools -jar "%%f" extract --destination "%DEPLOY_DIR%\backend-java"
)

:: 拷贝 Go 交付物
xcopy "%BACKEND_GO_DIR%\target" "%DEPLOY_DIR%\backend-go\" /E /I /H /Y >nul
xcopy "%BACKEND_GO_DIR%\config" "%DEPLOY_DIR%\backend-go\config\" /E /I /H /Y >nul

:: 拷贝 OCR 服务源码（compose 中 ocr-server 从 ./ocr-server 构建）
mkdir "%DEPLOY_DIR%\ocr-server"
copy "%OCR_DIR%\Dockerfile" "%DEPLOY_DIR%\ocr-server\" >nul
copy "%OCR_DIR%\pyproject.toml" "%DEPLOY_DIR%\ocr-server\" >nul
copy "%OCR_DIR%\uv.lock" "%DEPLOY_DIR%\ocr-server\" >nul
copy "%OCR_DIR%\README.md" "%DEPLOY_DIR%\ocr-server\" >nul
xcopy "%OCR_DIR%\src" "%DEPLOY_DIR%\ocr-server\src\" /E /I /H /Y >nul

:: 拷贝前端交付物
xcopy "%FRONTEND_DIR%\dist" "%DEPLOY_HTML%\" /E /I /H /Y >nul

echo 构建和拷贝完成！
pause
