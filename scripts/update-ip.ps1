param (
    [string]$Region = "us-east-1",
    [string]$ApiId = "3hvdy2quhe",
    [string]$StageName = "desarrollo",
    [string]$InstanceName = "pedidos360-publica"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  PEDIDOS360 - SINCRONIZADOR DE IP PUBLICA (AWS ACADEMY)  " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Obtener la nueva IP publica de la EC2
Write-Host ""
Write-Host "1. Consultando IP publica de la instancia '$InstanceName'..." -ForegroundColor Yellow
$instanceJsonRaw = aws ec2 describe-instances --filters "Name=tag:Name,Values=$InstanceName" "Name=instance-state-name,Values=running" --region $Region --output json
$instanceJson = $instanceJsonRaw | ConvertFrom-Json

if (-not $instanceJson.Reservations -or $instanceJson.Reservations.Count -eq 0) {
    Write-Host "[ERROR] No se encontro la instancia '$InstanceName' en estado RUNNING." -ForegroundColor Red
    Write-Host "Verifica que la instancia EC2 este encendida en la consola de AWS." -ForegroundColor Red
    exit 1
}

$newIp = $instanceJson.Reservations[0].Instances[0].PublicIpAddress

if (-not $newIp) {
    Write-Host "[ERROR] La instancia no tiene una IP publica asignada." -ForegroundColor Red
    exit 1
}

Write-Host "-> Nueva IP publica detectada: $newIp" -ForegroundColor Green

# 2. Obtener integraciones actuales de API Gateway
Write-Host ""
Write-Host "2. Consultando integraciones en AWS API Gateway ($ApiId)..." -ForegroundColor Yellow
$integrationsRaw = aws apigatewayv2 get-integrations --api-id $ApiId --region $Region --output json
$integrationsJson = $integrationsRaw | ConvertFrom-Json

$updatedCount = 0

foreach ($integ in $integrationsJson.Items) {
    $currentUri = $integ.IntegrationUri
    if ($currentUri -match 'http://([0-9\.]+)(/.*)?$') {
        $oldIp = $Matches[1]
        $path = $Matches[2]
        if ($oldIp -ne $newIp) {
            $newUri = "http://${newIp}${path}"
            Write-Host "  Actualizando [$($integ.IntegrationId)]: $oldIp -> $newIp ($path)" -ForegroundColor Gray
            aws apigatewayv2 update-integration --api-id $ApiId --integration-id $integ.IntegrationId --integration-uri $newUri --region $Region --output json | Out-Null
            $updatedCount++
        }
    }
}

if ($updatedCount -gt 0) {
    Write-Host "-> Se actualizaron $updatedCount integraciones con la nueva IP." -ForegroundColor Green
    
    # 3. Desplegar cambios en la etapa desarrollo
    Write-Host ""
    Write-Host "3. Desplegando cambios en la etapa '$StageName'..." -ForegroundColor Yellow
    aws apigatewayv2 create-deployment --api-id $ApiId --stage-name $StageName --region $Region --output json | Out-Null
    Write-Host "-> Despliegue completado con exito." -ForegroundColor Green
} else {
    Write-Host "-> Todas las integraciones ya apuntaban a la IP actual ($newIp). No se requieren cambios." -ForegroundColor Green
}

# 4. Resumen
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  SINCRONIZACION EXITOSA" -ForegroundColor Green
Write-Host "  IP EC2:         $newIp" -ForegroundColor White
Write-Host "  URL Aplicacion: https://${ApiId}.execute-api.${Region}.amazonaws.com/${StageName}/" -ForegroundColor White
Write-Host "==========================================================" -ForegroundColor Cyan
