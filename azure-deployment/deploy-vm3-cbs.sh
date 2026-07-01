#!/bin/bash
set -e

echo "============================================"
echo "Installing VM3: Core Banking System (Bare-Metal)"
echo "============================================"

# Update and install Java 21, Maven, and PostgreSQL
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk maven postgresql postgresql-contrib curl wget

echo "Setting up PostgreSQL..."
sudo -u postgres psql -c "ALTER USER postgres PASSWORD 'postgres';"
sudo -u postgres psql -c "CREATE DATABASE cbs_debit;" || true
sudo -u postgres psql -c "CREATE DATABASE cbs_credit;" || true

# Modify Postgres to listen on all interfaces so VM2 can reach it
sudo sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/g" /etc/postgresql/14/main/postgresql.conf 2>/dev/null || true
sudo sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/g" /etc/postgresql/15/main/postgresql.conf 2>/dev/null || true
sudo sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/g" /etc/postgresql/16/main/postgresql.conf 2>/dev/null || true
echo "host all all 0.0.0.0/0 md5" | sudo tee -a /etc/postgresql/14/main/pg_hba.conf 2>/dev/null || true
echo "host all all 0.0.0.0/0 md5" | sudo tee -a /etc/postgresql/15/main/pg_hba.conf 2>/dev/null || true
echo "host all all 0.0.0.0/0 md5" | sudo tee -a /etc/postgresql/16/main/pg_hba.conf 2>/dev/null || true
sudo systemctl restart postgresql

echo "Fixing Repo Structure locally..."
cd /opt/APEX-UPI
if [ -d "services/services" ]; then
    mv services/services/* services/
    rm -rf services/services
fi
if [ -d "infra/infra" ]; then
    mv infra/infra/* infra/
    rm -rf infra/infra
fi

echo "Building CBS Service..."
cd /opt/APEX-UPI/services/npci_cbs/npci_cbs/cbs-service
mvn clean package -DskipTests

echo "Creating Systemd Service for CBS Service..."
cat << 'EOF' | sudo tee /etc/systemd/system/psp-cbs-service.service
[Unit]
Description=Core Banking System Service
After=network.target

[Service]
Type=simple
User=root
EnvironmentFile=/opt/APEX-UPI/azure-deployment/azure-production.env
ExecStart=/usr/bin/java -jar /opt/APEX-UPI/services/npci_cbs/npci_cbs/cbs-service/target/cbs-service-2.0.0.jar
Restart=always

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
echo "Starting CBS Service..."
sudo systemctl enable --now psp-cbs-service

echo "VM3 Deployment Complete! CBS Bank is live."
