# APEX-UPI TODO (TPAP callback 401)

## Completed / Verified
- [x] Verified `TpapAuthFilter.shouldNotFilter()` exempts BOTH:
  - `/api/v1/tpap/callback`
  - `/api/v1/tpap/callback/`
- [x] Inspected `tpap-ingress-service` controllers: **no callback controller exists** for `/api/v1/tpap/callback`.
- [x] Inspected `tpap-ingress-service` security config directory: **no `SecurityFilterChain` / `SecurityConfig`** found.

## Diagnostic work
- [x] Attempted to add filter diagnostic logging, but build pipeline is blocked.

## Blocker
- [ ] Fix Maven compilation failure in `tpap-ingress-service`:
  - `Fatal error compiling: ... lombok ... NoSuchFieldException com.sun.tools.javac.code.TypeTag :: UNKNOWN`
  - (Likely Lombok/JDK incompatibility.)

## After blocker is resolved
- [ ] Rebuild + restart `tpap-ingress-service`.
- [ ] Call endpoint:
  - `curl -v -X POST http://localhost:8080/api/v1/tpap/callback -H "Content-Type: application/json" -d '{}'`
- [ ] Capture runtime proof (tpap-ingress-service logs showing which layer emits 401).
- [ ] Re-run a fresh payment and capture:
  - `WEBHOOK_OUT` success
  - callback HTTP 200
  - TPAP callback received
  - TPAP status updated

