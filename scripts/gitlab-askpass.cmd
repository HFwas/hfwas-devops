@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0gitlab-askpass.ps1" %*
