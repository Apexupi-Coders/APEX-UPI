@echo off
echo ============================================================
echo   APEX-UPI PSP Switch — Startup Script
echo   Infrastructure + 3 Core Microservices
echo ============================================================
echo.

echo [1/5] Starting infrastructure (Zookeeper, Kafka, Redis, PostgreSQL, WireMock)...
docker compose -f "%~dp0..\docker-compose.yml" up -d
if %ERRORLEVEL% neq 0 (
    echo ERROR: docker compose failed. Is Docker Desktop running?
    pause
    exit /b 1
)

echo.
echo [2/5] Waiting 35 seconds for Kafka broker to be ready...
ping -n 36 127.0.0.1 >nul 2>&1

echo.
echo [3/5] Starting tpap-ingress-service on port 8080...
start "APEX-UPI: Ingress (8080)" cmd /k "cd /d %~dp0..\..\services\tpap-ingress-service && mvn clean spring-boot:run -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8""

echo Waiting 15 seconds for ingress to initialize...
ping -n 16 127.0.0.1 >nul 2>&1

echo.
echo [4/5] Starting transaction-orchestrator on port 8082...
start "APEX-UPI: Orchestrator (8082)" cmd /k "cd /d %~dp0..\..\services\transaction-orchestrator && mvn clean spring-boot:run -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8""

echo Waiting 15 seconds for orchestrator to initialize...
ping -n 16 127.0.0.1 >nul 2>&1

echo.
echo [5/5] Starting npci-adapter on port 8081...
start "APEX-UPI: NPCI Adapter (8081)" cmd /k "cd /d %~dp0..\..\services\npci-adapter && mvn clean spring-boot:run -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8""

echo.
echo ============================================================
echo   All services are starting. Check each terminal window.
echo ============================================================
echo.
echo   Ingress Service:        http://localhost:8080
echo   Transaction Orchestrator: http://localhost:8082
echo   NPCI Adapter:           http://localhost:8081
echo   WireMock (NPCI Mock):   http://localhost:9090
echo.
echo   Swagger UI:             http://localhost:8080/swagger-ui/index.html
echo   Dashboard:              Open ..\dashboard\index.html in browser
echo.
echo   Test with:
echo     curl -X POST http://localhost:8080/tpap/api/v1/payment/initiate ^
echo       -H "Content-Type: application/json" ^
echo       -H "X-TPAP-ID: phonepe" ^
echo       -d "{\"txnId\":\"phonepe-550e8400-e29b-41d4-a716-446655440000\",\"payerVpa\":\"alice@apexupi\",\"payeeVpa\":\"bob@apexupi\",\"amount\":\"500.00\",\"currency\":\"INR\",\"encryptedPin\":\"ENC_PIN_DEMO\",\"deviceFingerprint\":\"FP_DEMO_001\",\"txnType\":\"PEER_TO_PEER\",\"remarks\":\"HPE Demo payment\"}"
echo.
echo   Or run: test-happy-path.bat
echo ============================================================
pause
