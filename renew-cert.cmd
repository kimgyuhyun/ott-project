@echo off
REM Renew Let's Encrypt certificates and verify the result.
REM
REM [2026-09-02] The logic moved to security\renew-cert.ps1. This file stays as the
REM entry point because three scheduled tasks point at it (LetsEncryptRenew,
REM LetsEncryptRenew_Evening, LetsEncryptRenew_OnLogon) - moving them is host state,
REM not repo state.
REM
REM Why it moved: batch takes the LAST command's exit code as the script's, so the
REM old two-liner (certbot renew, then nginx reload) reported success whenever the
REM reload worked - even if certbot had failed. `exit /b` below propagates the real one.
powershell.exe -NonInteractive -ExecutionPolicy Bypass -File "C:\solo-project\ott-project\security\renew-cert.ps1"
exit /b %ERRORLEVEL%
