@echo off
setlocal EnableDelayedExpansion

set "APP_NAME=framework"
set "SRC_DIR=src\java"
set "BUILD_DIR=build"
set "LIB_DIR=lib"
set "SERVLET_API_JAR=%LIB_DIR%\servlet-api.jar"

echo Nettoyage...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
mkdir "%BUILD_DIR%\classes"

echo Compilation...
set "SRCS="
for /r "%SRC_DIR%" %%f in (*.java) do set "SRCS=!SRCS! "%%f""

javac ^
    -encoding UTF-8 ^
    -cp "%SERVLET_API_JAR%" ^
    -d "%BUILD_DIR%\classes" ^
    %SRCS%

if errorlevel 1 (
    echo Erreur de compilation
    exit /b 1
)

echo Creation du JAR...
mkdir "%BUILD_DIR%\jar"

jar cf "%BUILD_DIR%\jar\%APP_NAME%.jar" ^
    -C "%BUILD_DIR%\classes" .

echo.
echo JAR cree : %BUILD_DIR%\jar\%APP_NAME%.jar
echo.

endlocal