@echo off
setlocal EnableExtensions
title Editor EXIF - Generazione APK
cd /d "%~dp0"

echo ==================================================
echo        EDITOR EXIF - GENERAZIONE APK
echo ==================================================
echo.

set "SDK=%ANDROID_HOME%"
if "%SDK%"=="" set "SDK=%ANDROID_SDK_ROOT%"
if "%SDK%"=="" if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if "%SDK%"=="" if exist "%USERPROFILE%\AppData\Local\Android\Sdk" set "SDK=%USERPROFILE%\AppData\Local\Android\Sdk"

if not defined SDK (
  echo ERRORE: Android SDK non trovato.
  echo Installa Android Studio e Android SDK Platform 35.
  echo Poi riapri questo file.
  goto :fail
)
echo Android SDK: %SDK%

set "JAVA_HOME="
if defined STUDIO_JDK if exist "%STUDIO_JDK%\bin\java.exe" set "JAVA_HOME=%STUDIO_JDK%"
if not defined JAVA_HOME if defined ANDROID_STUDIO_JDK if exist "%ANDROID_STUDIO_JDK%\bin\java.exe" set "JAVA_HOME=%ANDROID_STUDIO_JDK%"
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jre\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jre"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

where java >nul 2>nul
if errorlevel 1 (
  echo ERRORE: Java non trovato. Installa Android Studio con JBR incluso.
  goto :fail
)

set "SDKMANAGER=%SDK%\cmdline-tools\latest\bin\sdkmanager.bat"
if not exist "%SDKMANAGER%" (
  echo ERRORE: sdkmanager non trovato in:
  echo %SDKMANAGER%
  echo.
  echo Apri Android Studio - More Actions - SDK Manager - SDK Tools
  echo e installa Android SDK Command-line Tools (latest).
  goto :fail
)

echo.
echo Verifica componenti Android...
call "%SDKMANAGER%" --sdk_root="%SDK%" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
if errorlevel 1 (
  echo ERRORE durante installazione/verifica SDK.
  goto :fail
)

set "GRADLE_HOME=%~dp0.tools\gradle-8.9"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo.
  echo Scarico Gradle 8.9 (solo al primo avvio)...
  if not exist "%~dp0.tools" mkdir "%~dp0.tools"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile '%~dp0.tools\gradle-8.9-bin.zip'; Expand-Archive -LiteralPath '%~dp0.tools\gradle-8.9-bin.zip' -DestinationPath '%~dp0.tools' -Force"
  if errorlevel 1 (
    echo ERRORE: impossibile scaricare Gradle. Controlla la connessione internet.
    goto :fail
  )
)
set "PATH=%GRADLE_HOME%\bin;%PATH%"

echo.
echo Compilazione APK...
call gradle --no-daemon --stacktrace assembleDebug
if errorlevel 1 (
  echo.
  echo COMPILAZIONE NON RIUSCITA.
  echo Copia il testo dell'errore e invialo a ChatGPT per la correzione.
  goto :fail
)

if not exist "%~dp0APK" mkdir "%~dp0APK"
copy /Y "%~dp0app\build\outputs\apk\debug\app-debug.apk" "%~dp0APK\EditorEXIF.apk" >nul
if errorlevel 1 (
  echo APK generato ma impossibile copiarlo nella cartella APK.
  goto :fail
)

echo.
echo ==================================================
echo APK CREATO CORRETTAMENTE:
echo %~dp0APK\EditorEXIF.apk
echo ==================================================
echo.
echo Copia EditorEXIF.apk sul tablet o smartphone e aprilo
echo per installarlo. Potrebbe essere necessario autorizzare
echo l'installazione da questa origine.
pause
exit /b 0

:fail
echo.
echo Procedura interrotta. Leggi il messaggio sopra.
pause
exit /b 1
