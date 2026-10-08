param (
    [string]$Region = "us-east-1",
    [string]$ApiId = "4dyx4unmg5",
    [string]$TenantId = "e5372bf0-c5e3-4286-887c-79069f209c1f",
    [string]$Audience = "96aa35e3-1c70-4e36-8a41-0e1089f78df1",
    # Solo el origen publico del frontend: nada de "*"
    [string[]]$AllowedOrigins = @("https://44-221-145-83.nip.io")
)

# Crea (o reutiliza) un autorizador JWT de Entra ID en API Gateway HTTP y lo aplica a todas las rutas.
# Issuer y audience coinciden con lo que validan los microservicios Spring Boot (token v2.0).

# Con -File los valores llegan como un solo texto separado por comas
$AllowedOrigins = $AllowedOrigins -join "," -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ }

$issuer ="https://login.microsoftonline.com/$TenantId/v2.0"
$authorizerName = "entra-id-jwt"

Write-Host "1. Buscando autorizador '$authorizerName'..." -ForegroundColor Yellow
$authorizerId = aws apigatewayv2 get-authorizers --region $Region --api-id $ApiId `
    --query "Items[?Name=='$authorizerName'].AuthorizerId | [0]" --output text

if (-not $authorizerId -or $authorizerId -eq "None") {
    $authorizerId = aws apigatewayv2 create-authorizer --region $Region --api-id $ApiId `
        --name $authorizerName --authorizer-type JWT `
        --identity-source '$request.header.Authorization' `
        --jwt-configuration "Audience=$Audience,Issuer=$issuer" `
        --query "AuthorizerId" --output text
    Write-Host "-> Autorizador creado: $authorizerId" -ForegroundColor Green
} else {
    Write-Host "-> Ya existe: $authorizerId" -ForegroundColor Green
}

# Con CORS configurado en la API, API Gateway responde el preflight OPTIONS sin pasar por el autorizador
Write-Host "2. Configurando CORS para: $($AllowedOrigins -join ', ')" -ForegroundColor Yellow
$cors = @{
    AllowOrigins = $AllowedOrigins
    AllowMethods = @("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    AllowHeaders = @("authorization", "content-type")
    MaxAge       = 3600
} | ConvertTo-Json -Compress
$corsFile = New-TemporaryFile
Set-Content -Path $corsFile -Value $cors -Encoding ascii
aws apigatewayv2 update-api --region $Region --api-id $ApiId --cors-configuration "file://$corsFile" --query "ApiId" --output text | Out-Null
Remove-Item $corsFile

Write-Host "3. Protegiendo rutas..." -ForegroundColor Yellow
$routes = aws apigatewayv2 get-routes --region $Region --api-id $ApiId --output json | ConvertFrom-Json

# Las rutas ANY tambien capturan OPTIONS y el preflight no lleva token: cada ruta necesita su
# OPTIONS publico con la misma ruta (API Gateway prioriza la ruta mas especifica sobre el metodo)
foreach ($route in $routes.Items | Where-Object { $_.RouteKey -like "ANY *" }) {
    $optionsKey = $route.RouteKey -replace "^ANY ", "OPTIONS "
    $existing = $routes.Items | Where-Object { $_.RouteKey -eq $optionsKey }
    if ($existing) {
        aws apigatewayv2 update-route --region $Region --api-id $ApiId --route-id $existing.RouteId `
            --authorization-type NONE --target $route.Target --query "RouteKey" --output text
    } else {
        aws apigatewayv2 create-route --region $Region --api-id $ApiId --route-key $optionsKey `
            --authorization-type NONE --target $route.Target --query "RouteKey" --output text
    }
}

foreach ($route in $routes.Items | Where-Object { $_.RouteKey -notlike "OPTIONS *" }) {
    aws apigatewayv2 update-route --region $Region --api-id $ApiId --route-id $route.RouteId `
        --authorization-type JWT --authorizer-id $authorizerId --query "RouteKey" --output text
}

Write-Host ""
Write-Host "Listo. Sin token las rutas responden 401 desde API Gateway." -ForegroundColor Cyan
aws apigatewayv2 get-routes --region $Region --api-id $ApiId --query "Items[].[RouteKey,AuthorizationType]" --output table
