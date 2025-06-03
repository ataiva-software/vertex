@echo off
REM Eden DevOps Suite CLI Launcher Script for Windows
REM This script provides a convenient way to run the Eden CLI on Windows

setlocal enabledelayedexpansion

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
set "CLI_DIR=%SCRIPT_DIR%.."

REM Default Java options
if not defined JAVA_OPTS set "JAVA_OPTS=-Xmx512m -Xms128m"

REM Eden CLI JAR location
set "EDEN_JAR=%CLI_DIR%\eden.jar"

REM Check if Java is available
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ Error: Java is not installed or not in PATH
    echo Please install Java 11 or higher to run Eden CLI
    exit /b 1
)

REM Check if Eden JAR exists
if not exist "%EDEN_JAR%" (
    echo ❌ Error: Eden CLI JAR not found at %EDEN_JAR%
    echo Please ensure Eden CLI is properly installed
    exit /b 1
)

REM Set up environment
set "EDEN_CLI_HOME=%CLI_DIR%"
if not defined EDEN_CONFIG_DIR set "EDEN_CONFIG_DIR=%USERPROFILE%\.eden"

REM Create config directory if it doesn't exist
if not exist "%EDEN_CONFIG_DIR%" mkdir "%EDEN_CONFIG_DIR%"

REM Handle special commands
if "%1"=="--version" goto :version
if "%1"=="-v" goto :version
if "%1"=="--help" goto :help
if "%1"=="-h" goto :help
if "%1"=="--java-opts" goto :java_opts
if "%1"=="--debug" goto :debug

REM Run Eden CLI with all arguments
java %JAVA_OPTS% -jar "%EDEN_JAR%" %*
goto :eof

:version
echo 🌟 Eden DevOps Suite CLI
echo Version: 1.0.0-alpha
echo Platform: Windows
echo Home: %EDEN_CLI_HOME%
goto :eof

:help
java %JAVA_OPTS% -jar "%EDEN_JAR%" help
goto :eof

:java_opts
echo Current Java options: %JAVA_OPTS%
echo To customize: set JAVA_OPTS=-Xmx1g -Xms256m
goto :eof

:debug
echo 🔍 Debug Information:
echo   Script Dir: %SCRIPT_DIR%
echo   CLI Dir: %CLI_DIR%
echo   Eden JAR: %EDEN_JAR%
echo   Java Options: %JAVA_OPTS%
echo   Config Dir: %EDEN_CONFIG_DIR%
goto :eof