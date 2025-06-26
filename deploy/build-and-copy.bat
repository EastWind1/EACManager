chcp 65001

@echo off

echo 打包后端
cd ..
cd backend
call mvn clean

call mvn package
echo 打包前端
cd ../front
call pnpm build
cd ../deploy

echo 拷贝交付物
set ROOT_DIR=%~dp0%..\
set BACKEND_DIR=%ROOT_DIR%backend\target
set FRONT_DIR=%ROOT_DIR%front\dist
set DEPLOY_DIR=%ROOT_DIR%deploy\
set FRONT_DEPLOY_DIR=%DEPLOY_DIR%front\nginx\html
set BACKEND_DEPLOY_DIR=%DEPLOY_DIR%backend

rmdir /S /Q %BACKEND_DEPLOY_DIR%\lib
rmdir /S /Q %FRONT_DEPLOY_DIR%

for %%f in ("%BACKEND_DIR%\*.jar") do copy /Y "%%f" "%BACKEND_DEPLOY_DIR%\app.jar"
xcopy /E /I /Y "%BACKEND_DIR%\lib" "%BACKEND_DEPLOY_DIR%\lib"

xcopy /E /I /Y "%FRONT_DIR%" "%FRONT_DEPLOY_DIR%"