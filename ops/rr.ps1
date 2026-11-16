param(
    [Parameter(Mandatory=$true,Position=0)][ValidateSet('verify')][string]$Command,
    [Parameter(Mandatory=$true)][string]$Suite,
    [Parameter(Mandatory=$true)][string]$Task
)
$ErrorActionPreference = 'Stop'
$rrPython = Get-Command python -ErrorAction SilentlyContinue
if (-not $rrPython) { $rrPython = Get-Command python3 -ErrorAction SilentlyContinue }
if (-not $rrPython) { Write-Error 'BLOCKED: Python 3 is required for the command recorder.'; exit 2 }
& $rrPython.Source (Join-Path $PSScriptRoot 'run_commands.py') --suite $Suite --task $Task
exit $LASTEXITCODE
