#!/bin/bash
# ZeroPay Redis TLS Certificate Generation Script
# This generates self-signed certificates for development/testing
# For production, use Let's Encrypt or your organization's CA

set -e

echo "🔐 Generating TLS Certificates for Redis..."
echo "==========================================="

# Create TLS directory if it doesn't exist
mkdir -p ./tls
cd ./tls

# Step 1: Generate CA (Certificate Authority) key and certificate
echo ""
echo "Step 1: Generating CA key and certificate..."
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 1024 -out ca.crt \
  -subj "/C=US/ST=California/L=SanFrancisco/O=ZeroPay/OU=Security/CN=ZeroPay-CA"

echo "✅ CA certificate created: ca.crt"
echo "✅ CA key created: ca.key"

# Step 2: Generate Redis server key
echo ""
echo "Step 2: Generating Redis server key..."
openssl genrsa -out redis.key 4096
echo "✅ Redis key created: redis.key"

# Step 3: Generate Certificate Signing Request (CSR)
echo ""
echo "Step 3: Generating Certificate Signing Request..."
openssl req -new -key redis.key -out redis.csr \
  -subj "/C=US/ST=California/L=SanFrancisco/O=ZeroPay/OU=Backend/CN=localhost"

echo "✅ CSR created: redis.csr"

# Step 4: Sign the certificate with our CA
echo ""
echo "Step 4: Signing Redis certificate with CA..."
openssl x509 -req -in redis.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out redis.crt -days 500 -sha256

echo "✅ Redis certificate created: redis.crt"

# Step 5: Set proper permissions
echo ""
echo "Step 5: Setting file permissions..."
chmod 600 redis.key ca.key
chmod 644 redis.crt ca.crt

echo "✅ Permissions set (keys: 600, certs: 644)"

# Step 6: Clean up CSR
rm redis.csr
echo "✅ Cleaned up temporary files"

# Step 7: Verify certificates
echo ""
echo "Step 7: Verifying certificates..."
echo ""
echo "CA Certificate:"
openssl x509 -in ca.crt -noout -subject -dates
echo ""
echo "Redis Certificate:"
openssl x509 -in redis.crt -noout -subject -dates
echo ""

# List generated files
echo "==========================================="
echo "✅ TLS Certificates Generated Successfully!"
echo "==========================================="
echo ""
echo "Generated files in ./tls/:"
ls -lh
echo ""
echo "📋 Next steps:"
echo "   1. Keep ca.key and redis.key secure (never commit to git!)"
echo "   2. Add *.key to .gitignore"
echo "   3. For production, replace with certificates from trusted CA"
echo ""
