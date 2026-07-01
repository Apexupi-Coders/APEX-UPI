#!/bin/bash
set -e

echo "============================================"
echo "Installing VM2: NPCI Network (Bare-Metal)"
echo "============================================"

# Update and install Java 21 and Maven
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk maven curl wget

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

echo "Building NPCI Router..."
cd /opt/APEX-UPI/services/npci_cbs/npci_cbs/npci-router
mvn clean package -DskipTests

echo "Creating Systemd Service for NPCI Router..."
cat << 'EOF' | sudo tee /etc/systemd/system/psp-npci-router.service
[Unit]
Description=NPCI Router Mock Service
After=network.target

[Service]
Type=simple
User=root
EnvironmentFile=/opt/APEX-UPI/azure-deployment/azure-production.env
ExecStart=/usr/bin/java -jar /opt/APEX-UPI/services/npci_cbs/npci_cbs/npci-router/target/npci-router-2.0.0.jar
Restart=always

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
echo "Starting NPCI Router Service..."
sudo systemctl enable --now psp-npci-router

echo "VM2 Deployment Complete! NPCI Network is live."
