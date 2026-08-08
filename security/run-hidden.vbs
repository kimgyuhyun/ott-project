' Launches a PowerShell script with no console window at all.
'
' Why this exists: the scheduled tasks run with LogonType=Interactive, and in that
' mode powershell.exe always gets a console allocated before -WindowStyle Hidden can
' hide it, so a window flashes on every run (once a minute for the uptime monitor).
' wscript.exe has no console of its own, so Run(..., 0, False) never creates one.
'
' The clean alternative is LogonType=S4U like the OTT Security Watchdog task, but
' changing a task principal to S4U requires elevation; this wrapper does not.
'
' Usage:  wscript.exe run-hidden.vbs "<full path to .ps1>"

' Waits for the script and passes its exit code back, so Task Scheduler's
' LastTaskResult still reflects whether the run succeeded.

Dim args, cmd, rc
Set args = WScript.Arguments
If args.Count < 1 Then
  WScript.Quit 2
End If
cmd = "powershell.exe -NonInteractive -ExecutionPolicy Bypass -File """ & args(0) & """"
rc = CreateObject("WScript.Shell").Run(cmd, 0, True)
WScript.Quit rc
