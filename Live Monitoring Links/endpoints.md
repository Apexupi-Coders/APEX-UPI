# Executive Demo Control Links

These endpoints control the live demonstration environment, allowing you to force failures and trigger background reconciliation via the active Ngrok tunnel.

| Action | HTTP Method | Endpoint URL |
|:---|:---|:---|
| **Live Dashboard Data** | `GET` | [https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/status](https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/status) |
| **Enable NPCI Failure** | `POST` | [https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/npci-failure?enabled=true](https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/npci-failure?enabled=true) |
| **Disable NPCI Failure** | `POST` | [https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/npci-failure?enabled=false](https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/npci-failure?enabled=false) |
| **Enable CBS Failure** | `POST` | [https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/cbs-failure?enabled=true](https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/cbs-failure?enabled=true) |
| **Disable CBS Failure** | `POST` | [https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/cbs-failure?enabled=false](https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/cbs-failure?enabled=false) |
| **Trigger Reconciliation** | `GET` | [https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/reconcile-now](https://frayed-hurler-retiree.ngrok-free.dev/api/v1/control/reconcile-now) |
