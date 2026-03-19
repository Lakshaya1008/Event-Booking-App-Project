# Loads variables from .env and starts Spring Boot locally.
# Usage: .\start-local.ps1

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $repoRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error ".env file not found at $envFile. Copy .env.example to .env and fill values."
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) { return }

    $parts = $line.Split('=', 2)
    if ($parts.Count -eq 2) {
        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

Set-Location $repoRoot
mvn spring-boot:run

