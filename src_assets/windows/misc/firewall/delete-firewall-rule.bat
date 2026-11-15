@echo off

set RULE_NAME=NOUS
netsh advfirewall firewall delete rule name=Apollo >nul 2>&1

rem Delete the rule
netsh advfirewall firewall delete rule name=%RULE_NAME%
