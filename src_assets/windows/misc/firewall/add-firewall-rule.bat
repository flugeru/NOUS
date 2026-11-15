@echo off

rem Get sunshine root directory
for %%I in ("%~dp0\..") do set "ROOT_DIR=%%~fI"

set RULE_NAME=NOUS
netsh advfirewall firewall delete rule name=Apollo >nul 2>&1
set PROGRAM_BIN="%ROOT_DIR%\NOUS.exe"

rem Allow compatibility service child as well as canonical desktop executable.
netsh advfirewall firewall add rule name=NOUS dir=in action=allow program="%ROOT_DIR%\sunshine.exe" enable=yes
netsh advfirewall firewall add rule name=%RULE_NAME% dir=in action=allow protocol=tcp program=%PROGRAM_BIN% enable=yes
netsh advfirewall firewall add rule name=%RULE_NAME% dir=in action=allow protocol=udp program=%PROGRAM_BIN% enable=yes
