# Bootstrap helper for the Podman dev stack + Portainer on Windows.
#
# Flow:
#   1. Ask whether to rebuild the vi-app image.
#      - If YES: podman build -> compose down -> compose up -d, then Portainer.
#      - If NO : skip directly to Portainer (assumes stack is already up).
#   2. Always (re)start vi-portainer via `podman run` (the socket bind
#      cannot be expressed in podman-compose on Windows -- see docker-compose.yml).
#
# Run from anywhere:
#   .\src\docker\portainer.ps1

$ErrorActionPreference = 'Stop'

# Resolve repo root so relative compose/Dockerfile paths work regardless of CWD.
$RepoRoot   = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$composeFile = 'src/docker/docker-compose.yml'
$dockerfile  = 'src/docker/Dockerfile'
$network     = 'docker_default'   # auto-created by podman-compose from the compose-file directory name

Set-Location $RepoRoot

function Invoke-Native {
    param([string]$Label, [scriptblock]$Cmd, [switch]$AllowFail)
    Write-Host ">> $Label" -ForegroundColor Cyan
    & $Cmd
    if ($LASTEXITCODE -ne 0 -and -not $AllowFail) {
        throw "$Label failed (exit=$LASTEXITCODE)"
    }
}

# 1. Ask about rebuild
$answer = Read-Host 'Vuoi buildare l''app prima di avviare lo stack? (y/N)'
$doBuild = $answer -match '^(y|Y|yes|YES|s|S|si|SI)$'

if ($doBuild) {
    Invoke-Native 'podman build vi-app:latest' {
        podman build -f $dockerfile -t vi-app:latest .
    }

    Invoke-Native 'podman-compose down' -AllowFail {
        podman-compose -f $composeFile down
    }

    Invoke-Native 'podman-compose up -d' {
        podman-compose -f $composeFile up -d
    }
} else {
    Write-Host 'Skip build/restart - using existing running stack.' -ForegroundColor DarkGray
}

# 2. (Re)start Portainer
$existing = podman ps -a --filter 'name=^vi-portainer$' --format '{{.Names}}'
if ($existing -eq 'vi-portainer') {
    Invoke-Native 'remove existing vi-portainer' {
        podman rm -f vi-portainer | Out-Null
    }
}

Invoke-Native 'podman run vi-portainer' {
    podman run -d `
        --name vi-portainer `
        --restart unless-stopped `
        --network $network `
        -p 9000:9000 `
        -p 9443:9443 `
        -v /run/user/1000/podman/podman.sock:/var/run/docker.sock `
        -v portainer-data:/data `
        portainer/portainer-ce:latest | Out-Null
}

Write-Host ''
Write-Host 'Done.' -ForegroundColor Green
Write-Host 'Portainer UI: http://localhost:9000 (HTTPS: https://localhost:9443)'
podman ps --format 'table {{.Names}}`t{{.Status}}`t{{.Ports}}'
