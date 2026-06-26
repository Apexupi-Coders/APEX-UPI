# Deployment Documentation

## Contents

| Document | Description |
|---|---|
| [01_Deployment_Overview.md](./01_Deployment_Overview.md) | System-wide deployment architecture, VM topology for both VirtualBox and Azure, and high-level infrastructure summary |
| [02_VirtualBox_Local_Deployment.md](./02_VirtualBox_Local_Deployment.md) | Complete 4-VM setup guide for local development — VM specifications, network configuration, prerequisites, database setup, build, startup, and verification for every service |
| [03_Azure_Cloud_Deployment.md](./03_Azure_Cloud_Deployment.md) | Complete 3-VM setup guide for Azure — VM specifications, networking, NSG rules, service-to-VM mapping, configuration differences from local, and startup procedures |
| [04_Network_and_Port_Reference.md](./04_Network_and_Port_Reference.md) | Port allocation tables, inter-VM communication matrix, Kafka broker addressing, and firewall rules for both environments |
| [05_Database_Setup.md](./05_Database_Setup.md) | PostgreSQL installation, database creation scripts, table DDL, and seed data for all databases across all VMs |
