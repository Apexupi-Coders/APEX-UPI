@echo off
set "PATH=%PATH%;C:\Windows\System32;C:\Windows\System32\WindowsPowerShell\v1.0"
echo ============================================================
echo   APEX-UPI — Happy Path Integration Test
echo ============================================================
echo.

REM ── Generate a UUID-like txnId with phonepe prefix ──
for /f %%i in ('powershell -Command "[guid]::NewGuid().ToString()"') do set TXNUUID=%%i
set TXNID=phonepe-%TXNUUID%

echo [TEST 1] Payment Initiation via Ingress Service
echo   Endpoint: POST http://localhost:8080/tpap/api/v1/payment/initiate
echo   txnId: %TXNID%
echo.

curl -s -w "\n  HTTP Status: %%{http_code}\n" -X POST http://localhost:8080/tpap/api/v1/payment/initiate ^
  -H "Content-Type: application/json" ^
  -H "X-TPAP-ID: phonepe" ^
  -d "{\"txnId\":\"%TXNID%\",\"payerVpa\":\"alice@apexupi\",\"payeeVpa\":\"bob@apexupi\",\"amount\":\"500.00\",\"currency\":\"INR\",\"encryptedPin\":\"ENC_PIN_DEMO\",\"deviceFingerprint\":\"FP_DEMO_001\",\"txnType\":\"PEER_TO_PEER\",\"remarks\":\"HPE Demo payment\"}"

echo.
echo ────────────────────────────────────────────────────
echo.

echo [TEST 2] Waiting 3 seconds for async processing...
ping -n 4 127.0.0.1 >nul 2>&1

echo [TEST 2] Checking Ingress Health
echo   Endpoint: GET http://localhost:8080/tpap/api/v1/health
echo.

curl -s -w "\n  HTTP Status: %%{http_code}\n" http://localhost:8080/tpap/api/v1/health

echo.
echo ────────────────────────────────────────────────────
echo.

echo [TEST 3] Orchestrator Direct — Full Saga Test
echo   Endpoint: POST http://localhost:8082/api/v1/txn
echo.

for /f %%i in ('powershell -Command "[guid]::NewGuid().ToString()"') do set TXNUUID2=%%i
set TR=DEMO-%TXNUUID2%

curl -s -w "\n  HTTP Status: %%{http_code}\n" -X POST http://localhost:8082/api/v1/txn ^
  -H "Content-Type: application/json" ^
  -d "{\"tr\":\"%TR%\",\"pa\":\"bob@apexupi\",\"pn\":\"Bob\",\"mc\":\"0000\",\"am\":500.00,\"cu\":\"INR\",\"mode\":\"04\",\"flowDirection\":\"SEND\"}"

echo.
echo ────────────────────────────────────────────────────
echo.

echo [TEST 4] Waiting 4 seconds for saga completion...
ping -n 5 127.0.0.1 >nul 2>&1

echo [TEST 4] Polling Orchestrator Status
echo   Endpoint: GET http://localhost:8082/api/v1/control/status
echo.

curl -s -w "\n  HTTP Status: %%{http_code}\n" http://localhost:8082/api/v1/control/status

echo.
echo ────────────────────────────────────────────────────
echo.

echo [TEST 5] Duplicate Detection Test (re-send same txnId to ingress)
echo   Expected: HTTP 202 with idempotent replay
echo.

curl -s -w "\n  HTTP Status: %%{http_code}\n" -X POST http://localhost:8080/tpap/api/v1/payment/initiate ^
  -H "Content-Type: application/json" ^
  -H "X-TPAP-ID: phonepe" ^
  -d "{\"txnId\":\"%TXNID%\",\"payerVpa\":\"alice@apexupi\",\"payeeVpa\":\"bob@apexupi\",\"amount\":\"500.00\",\"currency\":\"INR\",\"encryptedPin\":\"ENC_PIN_DEMO\",\"deviceFingerprint\":\"FP_DEMO_001\",\"txnType\":\"PEER_TO_PEER\",\"remarks\":\"HPE Demo payment\"}"

echo.
echo ────────────────────────────────────────────────────
echo.

echo [TEST 6] Invalid Payload Test (missing payerVpa)
echo   Expected: HTTP 400
echo.

for /f %%i in ('powershell -Command "[guid]::NewGuid().ToString()"') do set TXNUUID3=%%i

curl -s -w "\n  HTTP Status: %%{http_code}\n" -X POST http://localhost:8080/tpap/api/v1/payment/initiate ^
  -H "Content-Type: application/json" ^
  -H "X-TPAP-ID: phonepe" ^
  -d "{\"txnId\":\"phonepe-%TXNUUID3%\",\"payeeVpa\":\"bob@apexupi\",\"amount\":\"500.00\",\"currency\":\"INR\",\"encryptedPin\":\"ENC_PIN_DEMO\",\"deviceFingerprint\":\"FP_DEMO_001\",\"txnType\":\"PEER_TO_PEER\"}"

echo.
echo ────────────────────────────────────────────────────
echo.

echo [TEST 7] NPCI Adapter Health
echo   Endpoint: GET http://localhost:8081/actuator/health
echo.

curl -s -w "\n  HTTP Status: %%{http_code}\n" http://localhost:8081/actuator/health

echo.
echo ============================================================
echo   All tests completed!
echo ============================================================
pause
