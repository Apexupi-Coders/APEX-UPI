# Configuration and Deployment

## 1. Deployment Topology

The APEX-UPI PSP Switch supports two deployment environments:

| Environment | Platform | Purpose |
|---|---|---|
| Local Development | Oracle VirtualBox | Developer workstation, integration testing |
| Cloud | Microsoft Azure | Staging and production deployment |

Each microservice is a self-contained Spring Boot JAR file. Services are deployed directly on virtual machines or Azure Virtual Machine Scale Sets. No containerization layer is used.

---

## 2. Infrastructure Components

The following infrastructure components must be provisioned before starting any PSP Switch service:

| Component | Purpose | Minimum Version |
|---|---|---|
| Apache Kafka | Asynchronous event bus | 3.4 |
| Apache ZooKeeper | Kafka broker coordination | 3.8 |
| PostgreSQL | Transactional state and ledger persistence | 15 |
| Redis | Idempotency cache and velocity counters | 7 |

### 2.1 Kafka Configuration

Key broker settings:

| Parameter | Value | Description |
|---|---|---|
| `auto.create.topics.enable` | `true` | Allows services to create topics on first use |
| `offsets.topic.replication.factor` | `1` (dev) / `3` (prod) | Replication for consumer offset topic |
| `log.retention.hours` | `168` (prod) | Message retention period |
| `listener.security.protocol.map` | `PLAINTEXT:PLAINTEXT` (dev) / `SSL:SSL` (prod) | Transport security |

### 2.2 PostgreSQL Configuration

Each service group uses the same PostgreSQL instance with logically separated schemas or databases as required.

| Parameter | Value |
|---|---|
| Database name | `apexupi` |
| Default username | `apexupi` |
| Connection pool | HikariCP (Spring Boot default) |
| Schema migration | Flyway (auto-applied on startup where configured) |

### 2.3 Redis Configuration

| Parameter | Value | Description |
|---|---|---|
| Persistence | RDB snapshots | Survives Redis restart |
| Eviction policy | `allkeys-lru` | Evicts least-recently used keys when memory is full |
| `maxmemory` | To be set per VM size | Prevents unbounded memory usage |

---

## 3. Environment Variables

### 3.1 TPAP Ingress Service

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `SPRING_DATA_REDIS_HOST` | Redis hostname |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address(es) |
| `server.port` | HTTP listening port |

### 3.2 Transaction Orchestrator

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `SPRING_DATA_REDIS_HOST` | Redis hostname |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address(es) |
| `ADAPTER_URL` | NPCI Adapter base URL |
| `crypto.key` | AES-256 encryption key for PII fields |
| `server.port` | HTTP listening port |

### 3.3 NPCI Adapter

| Variable | Description |
|---|---|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address(es) |
| `NPCI_BASE_URL` | NPCI endpoint base URL |
| `NPCI_MTLS_ENABLED` | Set to `true` to enable mTLS for NPCI transport |
| `NPCI_KEYSTORE_PATH` | Path to PKCS12 keystore file |
| `NPCI_TRUSTSTORE_PATH` | Path to PKCS12 truststore file |
| `server.port` | HTTP listening port |

### 3.4 NPCI Response Consumer

| Variable | Description |
|---|---|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address(es) |
| `server.port` | HTTP listening port |

### 3.5 Rules Validation Service

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection string |
| `SPRING_REDIS_HOST` | Redis hostname |
| `rules.min-amount` | Minimum allowed transaction amount (INR) |
| `rules.max-amount` | Maximum allowed transaction amount (INR) |
| `rules.max-daily-count` | Maximum daily transactions per VPA |
| `rules.max-daily-volume` | Maximum daily volume per VPA (INR) |
| `rules.max-per-minute` | Maximum transactions per minute per VPA |
| `server.port` | HTTP listening port |

### 3.6 Ledger Service

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address(es) |
| `crypto.key` | AES-256 key for PII field encryption |
| `server.port` | HTTP listening port |

### 3.7 PSP Ledger Service

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address(es) |
| `server.port` | HTTP listening port |

### 3.8 Audit Service

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection string |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address(es) |
| `server.port` | HTTP listening port |

### 3.9 TPAP Egress Service

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address(es) |
| `webhook.timeout-seconds` | HTTP client timeout for webhook delivery |
| `server.port` | HTTP listening port |

---

## 4. Service Port Reference

| Service | Default Port |
|---|---|
| TPAP Ingress Service | 8080 |
| Transaction Orchestrator | 8081 |
| NPCI Adapter | 8082 |
| NPCI Response Consumer | 8084 |
| Rules Validation Service | 8085 |
| Ledger Service | 8083 |
| PSP Ledger Service | 8086 |
| Audit Service | 8087 |
| TPAP Egress Service | 8088 |

---

## 5. Local Deployment — VirtualBox

For local development and integration testing, services are deployed on Oracle VirtualBox virtual machines running Ubuntu Server 22.04 LTS.

### Recommended VM Layout

| VM | Role | vCPU | RAM |
|---|---|---|---|
| `apex-infra` | Kafka, ZooKeeper, PostgreSQL, Redis | 2 | 4 GB |
| `apex-switch-01` | TPAP Ingress, Orchestrator, NPCI Adapter | 2 | 4 GB |
| `apex-switch-02` | Ledger, Audit, Egress, Rules, PSP Ledger | 2 | 4 GB |

### Network Configuration

Use a VirtualBox Host-Only Network adapter so all VMs share a private subnet (e.g., `192.168.56.0/24`). Add a NAT adapter for outbound internet access if required.

### Service Startup

Each service is started as a background process or configured as a `systemd` unit:

```bash
# Build the service JAR
cd services/psp-switch/<service-name>
mvn clean package -DskipTests

# Run as a background process
java -jar target/<artifact>.jar \
  --SPRING_DATASOURCE_URL=jdbc:postgresql://192.168.56.101:5432/apexupi \
  --SPRING_KAFKA_BOOTSTRAP_SERVERS=192.168.56.101:9092 \
  &
```

### Recommended: systemd Unit File

Create `/etc/systemd/system/apex-ingress.service`:

```ini
[Unit]
Description=APEX-UPI TPAP Ingress Service
After=network.target

[Service]
User=apexupi
ExecStart=/usr/bin/java -jar /opt/apex/tpap-ingress-service.jar
EnvironmentFile=/opt/apex/ingress.env
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable apex-ingress
sudo systemctl start apex-ingress
```

---

## 6. Cloud Deployment — Microsoft Azure

For cloud deployment, services are hosted on Azure Virtual Machines within a secured Virtual Network (VNet).

### Recommended Azure Resource Layout

| Resource | Type | Purpose |
|---|---|---|
| `apex-vnet` | Virtual Network | Private network for all services |
| `apex-infra-subnet` | Subnet | Infrastructure VMs (Kafka, PostgreSQL, Redis) |
| `apex-app-subnet` | Subnet | Application service VMs |
| `apex-nsg` | Network Security Group | Inbound/outbound firewall rules |
| `apex-postgres` | Azure Database for PostgreSQL (Flexible Server) | Managed PostgreSQL |
| `apex-redis` | Azure Cache for Redis | Managed Redis |
| `apex-kafka-vm` | Azure Virtual Machine (Standard_D2s_v3) | Self-managed Kafka |
| `apex-switch-vmss` | Virtual Machine Scale Set | Auto-scaling for application services |

### Azure Database for PostgreSQL

Using Azure's managed PostgreSQL (Flexible Server) removes the operational overhead of managing PostgreSQL replication and backups.

Configuration:
- SKU: `Standard_D2ds_v4` (2 vCores, 8 GB RAM)
- Storage: 128 GB with auto-grow enabled
- Backup retention: 7 days
- SSL enforcement: Enabled

Connection string format:
```
jdbc:postgresql://<server>.postgres.database.azure.com:5432/apexupi?sslmode=require
```

### Azure Cache for Redis

- SKU: `Standard C1` (1 GB)
- TLS: Enabled (port 6380)
- Persistence: RDB snapshots enabled

Connection string format uses the Azure Redis connection string with SSL.

### Networking and Security

- All inter-service communication remains within the VNet; no public IPs for internal services.
- TPAP Ingress Service is the only component exposed via an Azure Application Gateway (WAF-enabled) for TPAP-facing traffic.
- NPCI Adapter is the only component that requires outbound internet access to reach NPCI endpoints; this is controlled via a dedicated outbound NAT gateway.
- Network Security Group rules restrict Kafka (9092) and PostgreSQL (5432) ports to the application subnet only.

### Azure Key Vault Integration

Store all secrets (database passwords, crypto keys, TPAP tokens) in Azure Key Vault. Services retrieve secrets at startup using the Azure SDK or Spring Cloud Azure Key Vault Secrets integration.

---

## 7. Building Services

Each service is built independently with Apache Maven:

```bash
cd services/psp-switch/<service-name>
mvn clean package -DskipTests
```

The output JAR is located at `target/<artifact-id>-<version>.jar`.

To build all PSP Switch services:

```bash
for service in tpap-ingress-service transaction-orchestrator npci-adapter \
  npci-response-consumer rules-validation-service ledger-service \
  psp-ledger-service audit-service tpap-egress-service; do
  echo "Building $service..."
  (cd services/psp-switch/$service && mvn clean package -DskipTests)
done
```

---

## 8. Running Tests

Each service has its own test suite. Tests use an H2 in-memory database, embedded Kafka, and mock Redis where applicable.

```bash
cd services/psp-switch/<service-name>
mvn test
```

---

## 9. Prerequisites

| Prerequisite | Version | Notes |
|---|---|---|
| Java | 17 (most services) / 21 (psp-ledger-service, tpap-egress-service) | OpenJDK or Adoptium Temurin |
| Apache Maven | 3.8+ | Build tool |
| PostgreSQL client | 15+ | For schema verification |
| Apache Kafka | 3.4+ | Must be running before services start |
| Redis | 7+ | Must be running before Ingress and Orchestrator start |
