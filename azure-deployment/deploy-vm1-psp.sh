#!/bin/bash
set -e

echo "============================================"
echo "Installing VM1: PSP Switch (Bare-Metal)"
echo "============================================"

# Update and install Java 21, Maven, Redis, and PostgreSQL
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk maven redis-server postgresql postgresql-contrib curl wget

# Install Kafka/Zookeeper
KAFKA_VERSION="3.6.1"
SCALA_VERSION="2.13"
if [ ! -d "/opt/kafka" ]; then
    echo "Downloading Kafka..."
    wget https://archive.apache.org/dist/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz
    tar -xzf kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz
    sudo mv kafka_${SCALA_VERSION}-${KAFKA_VERSION} /opt/kafka
    rm -f kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz*
fi

echo "Creating Zookeeper Systemd Service..."
cat << 'EOF' | sudo tee /etc/systemd/system/zookeeper.service
[Unit]
Description=Apache Zookeeper server
Documentation=http://zookeeper.apache.org
Requires=network.target remote-fs.target
After=network.target remote-fs.target

[Service]
Type=simple
ExecStart=/opt/kafka/bin/zookeeper-server-start.sh /opt/kafka/config/zookeeper.properties
ExecStop=/opt/kafka/bin/zookeeper-server-stop.sh
Restart=on-abnormal
User=root

[Install]
WantedBy=multi-user.target
EOF

echo "Creating Kafka Systemd Service..."
cat << 'EOF' | sudo tee /etc/systemd/system/kafka.service
[Unit]
Description=Apache Kafka Server
Documentation=http://kafka.apache.org/documentation.html
Requires=zookeeper.service
After=zookeeper.service

[Service]
Type=simple
Environment="JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64"
ExecStart=/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties
ExecStop=/opt/kafka/bin/kafka-server-stop.sh
Restart=on-abnormal
User=root

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now zookeeper
sudo systemctl enable --now kafka

echo "Setting up PostgreSQL..."
sudo -u postgres psql -c "ALTER USER postgres PASSWORD 'postgres';"
sudo -u postgres psql -c "CREATE DATABASE tpap_ingress;" || true
sudo -u postgres psql -c "CREATE DATABASE orchestrator;" || true
sudo -u postgres psql -c "CREATE DATABASE egress_db;" || true
sudo -u postgres psql -c "CREATE DATABASE ledger_db;" || true

# Modify Postgres to listen on all interfaces
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

echo "Building PSP Switch microservices..."
cd /opt/APEX-UPI/services/psp-switch/tpap-ingress-service && mvn clean package -DskipTests
cd /opt/APEX-UPI/services/psp-switch/transaction-orchestrator && mvn clean package -DskipTests
cd /opt/APEX-UPI/services/psp-switch/npci-adapter && mvn clean package -DskipTests
cd /opt/APEX-UPI/services/psp-switch/tpap-egress-service && mvn clean package -DskipTests
cd /opt/APEX-UPI/services/psp-switch/npci-response-consumer && mvn clean package -DskipTests
cd /opt/APEX-UPI/services/psp-switch/ledger-service && mvn clean package -DskipTests

echo "Installing Systemd Services..."
cd /opt/APEX-UPI/infra/systemd
sudo cp psp-tpap-ingress.service psp-orchestrator.service psp-npci-adapter.service psp-tpap-egress.service psp-npci-response.service psp-ledger.service /etc/systemd/system/
sudo systemctl daemon-reload

echo "Starting Services..."
sudo systemctl enable --now psp-tpap-ingress
sudo systemctl enable --now psp-orchestrator
sudo systemctl enable --now psp-npci-adapter
sudo systemctl enable --now psp-tpap-egress
sudo systemctl enable --now psp-npci-response
sudo systemctl enable --now psp-ledger

echo "VM1 Deployment Complete!"
