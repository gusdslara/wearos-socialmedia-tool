@echo off
setlocal
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ADB=C:\Users\GUS\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set "WATCH=192.168.15.84:42335"
set "LOG=%~dp0build_log.txt"
set "SHOTS=%~dp0shots"
echo === BUILD %date% %time% === > "%LOG%"
call gradlew.bat assembleDebug >> "%LOG%" 2>&1
if errorlevel 1 (
  echo BUILD_FAILED >> "%LOG%"
  goto :end
)
echo BUILD_OK >> "%LOG%"
"%ADB%" disconnect %WATCH% >> "%LOG%" 2>&1
"%ADB%" connect %WATCH% >> "%LOG%" 2>&1
timeout /t 4 /nobreak > nul
"%ADB%" devices >> "%LOG%" 2>&1
"%ADB%" -s %WATCH% install -r app\build\outputs\apk\debug\app-debug.apk >> "%LOG%" 2>&1
if errorlevel 1 (
  echo INSTALL_FAILED >> "%LOG%"
  goto :end
)
echo INSTALL_OK >> "%LOG%"
if not exist "%SHOTS%" mkdir "%SHOTS%"
"%ADB%" -s %WATCH% shell wm size >> "%LOG%" 2>&1
"%ADB%" -s %WATCH% shell wm density >> "%LOG%" 2>&1
"%ADB%" -s %WATCH% logcat -c >> "%LOG%" 2>&1
for %%Z in (100 50 150) do (
  "%ADB%" -s %WATCH% shell am force-stop com.wearports.social >> "%LOG%" 2>&1
  "%ADB%" -s %WATCH% shell input keyevent KEYCODE_WAKEUP >> "%LOG%" 2>&1
  "%ADB%" -s %WATCH% shell am start -n com.wearports.social/.MainActivity --es open 1 --ei zoom %%Z >> "%LOG%" 2>&1
  timeout /t 18 /nobreak > nul
  "%ADB%" -s %WATCH% exec-out screencap -p > "%SHOTS%\insta_%%Z.png"
  echo SHOT %%Z >> "%LOG%"
)
"%ADB%" -s %WATCH% logcat -d -t 300 GeckoWebExtension:V SocialWear:V GeckoConsole:V *:S >> "%LOG%" 2>&1
:end
echo === FIM %time% === >> "%LOG%"
endlocal
