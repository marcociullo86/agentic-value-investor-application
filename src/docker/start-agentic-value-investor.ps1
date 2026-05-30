# Bootstrap helper for the Podman dev stack + Portainer on Windows.
#
# Flow:
#   1. Ask whether to rebuild the vi-app image.
#      - If YES: chiede CPU/GPU (scelta RUNTIME del sidecar → override compose,
#        applicato anche senza rebuild) e se ribuildare il sidecar (opzionale),
#        poi: podman build vi-app -> compose down -> [compose build sidecar] ->
#        compose up -d (con override GPU se scelto). Se GPU, verifica che il
#        sidecar veda CUDA. Then Portainer.
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
    # 1a. CPU oppure GPU per il sidecar embeddings. È una scelta RUNTIME: l'override
    #     docker-compose.gpu.yml (devices: nvidia.com/gpu=all) va applicato a
    #     down/up -d a PRESCINDERE dal rebuild, altrimenti il container riparte in
    #     CPU. GPU richiede, una tantum nella podman machine, nvidia-container-toolkit
    #     + spec CDI (`nvidia-ctk cdi generate`). Default CPU su Invio.
    $gpuAnswer = Read-Host 'Sidecar embeddings: CPU o GPU? (cpu/GPU) [default CPU]'
    $useGpu = $gpuAnswer -match '^(g|G|gpu|GPU)$'

    $composeArgs = @('-f', $composeFile)
    if ($useGpu) {
        $composeArgs += @('-f', 'src/docker/docker-compose.gpu.yml')
        Write-Host 'Sidecar: GPU (override docker-compose.gpu.yml, CDI nvidia.com/gpu=all)' -ForegroundColor Green
    } else {
        Write-Host 'Sidecar: CPU' -ForegroundColor DarkGray
    }

    # 1b. Rebuild del sidecar: opzionale e indipendente da CPU/GPU (serve solo se
    #     hai cambiato embeddings-sidecar/app.py o il Dockerfile del sidecar).
    $sidecarAnswer = Read-Host 'Vuoi ribuildare il sidecar embeddings? (y/N)'
    $rebuildSidecar = $sidecarAnswer -match '^(y|Y|yes|YES|s|S|si|SI)$'

    Invoke-Native 'podman build vi-app:latest' {
        podman build -f $dockerfile -t vi-app:latest .
    }

    Invoke-Native 'podman-compose down' -AllowFail {
        podman-compose @composeArgs down
    }

    # Rebuild del solo sidecar: recepisce le modifiche a embeddings-sidecar/app.py
    # (l'ultimo layer e' la COPY di app.py -> rebuild veloce; il download del
    #  modello resta cache-ato nei layer precedenti). Skippato se non richiesto.
    if ($rebuildSidecar) {
        Invoke-Native 'podman-compose build embeddings-sidecar' {
            podman-compose @composeArgs build embeddings-sidecar
        }
    } else {
        Write-Host 'Skip rebuild sidecar embeddings - uso immagine esistente.' -ForegroundColor DarkGray
    }

    Invoke-Native 'podman-compose up -d' {
        podman-compose @composeArgs up -d
    }

    # 1c. Se GPU: verifica che il sidecar veda davvero CUDA. Se il container è
    #     ripartito in CPU (override non applicato / CDI assente) lo segnaliamo
    #     subito invece di scoprirlo da un ingest lentissimo.
    if ($useGpu) {
        Write-Host 'Verifica CUDA nel sidecar...' -ForegroundColor Cyan
        Start-Sleep -Seconds 8
        $cuda = podman exec vi-embeddings-sidecar python -c "import torch; print(torch.cuda.is_available())" 2>$null
        if ($cuda -match 'True') {
            Write-Host "Sidecar GPU OK: torch.cuda.is_available() = True" -ForegroundColor Green
        } else {
            Write-Host "ATTENZIONE: sidecar NON vede la GPU (cuda_available=$cuda). Gira in CPU." -ForegroundColor Yellow
            Write-Host "  Controlla: nvidia-ctk cdi list  (deve elencare nvidia.com/gpu=all)" -ForegroundColor Yellow
        }
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
