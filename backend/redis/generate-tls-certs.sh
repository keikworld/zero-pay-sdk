#!/bin/bash

################################################################################
# ZeroPay - TLS Certificate Generation Script
# 
# Generates self-signed TLS certificates for Redis (development)
# 
# Security Notes:
# - Self-signed certificates are for DEVELOPMENT ONLY
# - Production should use Let's Encrypt or commercial CA
# - Certificates expire in 365 days
# - 4096-bit RSA keys for strong security
# 
# Usage:
#   cd backend/redis
#   ./generate-tls-certs.sh
# 
# Output:
#   tls/ca.crt        - CA certificate (public)
#   tls/ca.key        - CA private key (SECRET)
#   tls/redis.crt     - Redis certificate (public)
#   tls/redis.key     - Redis private key (SECRET)
# 
# @version 1.0.0
# @date 2025-10-11
################################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CERT_DIR="tls"
VALIDITY_DAYS=365
KEY_SIZE=4096
COUNTRY="US"
STATE="California"
CITY="San Francisco"
ORG="ZeroPay"
CN_CA="ZeroPay Root CA"
CN_REDIS="localhost"

################################################################################
# Helper Functions
################################################################################

print_header() {
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

################################################################################
# Pre-flight Checks
################################################################################

print_header "Pre-flight Checks"

# Check if openssl is installed
if ! command -v openssl &> /dev/null; then
    print_error "OpenSSL is not installed"
    echo "Install with: brew install openssl (macOS) or apt-get install openssl (Linux)"
    exit 1
fi
print_success "OpenSSL found: $(openssl version)"

# Check if we're in the correct directory
if [ ! -f "redis.conf" ]; then
    print_error "redis.conf not found. Please run this script from backend/redis/ directory"
    echo "Usage: cd backend/redis && ./generate-tls-certs.sh"
    exit 1
fi
print_success "Running from correct directory"

################################################################################
# Setup
################################################################################

print_header "Setup TLS Directory"

# Create TLS directory if it doesn't exist
if [ ! -d "$CERT_DIR" ]; then
    mkdir -p "$CERT_DIR"
    print_success "Created $CERT_DIR directory"
else
    print_info "TLS directory already exists"
    
    # Check if certificates already exist
    if [ -f "$CERT_DIR/redis.crt" ] || [ -f "$CERT_DIR/ca.crt" ]; then
        print_warning "Certificates already exist!"
        read -p "Do you want to regenerate them? This will overwrite existing certificates. (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "Keeping existing certificates. Exiting."
            exit 0
        fi
        print_info "Regenerating certificates..."
    fi
fi

# Change to TLS directory
cd "$CERT_DIR"

################################################################################
# Generate CA (Certificate Authority)
################################################################################

print_header "Generating Certificate Authority (CA)"

# Generate CA private key
print_info "Generating CA private key ($KEY_SIZE bits)..."
openssl genrsa -out ca.key $KEY_SIZE 2>/dev/null

# Set secure permissions on CA private key
chmod 600 ca.key
print_success "CA private key generated: ca.key"

# Generate CA certificate
print_info "Generating CA certificate..."
openssl req -new -x509 -days $VALIDITY_DAYS -key ca.key -out ca.crt \
    -subj "/C=$COUNTRY/ST=$STATE/L=$CITY/O=$ORG/CN=$CN_CA" \
    2>/dev/null

print_success "CA certificate generated: ca.crt"

# Display CA certificate info
print_info "CA Certificate Details:"
openssl x509 -in ca.crt -noout -subject -issuer -dates

################################################################################
# Generate Redis Server Certificate
################################################################################

print_header "Generating Redis Server Certificate"

# Generate Redis private key
print_info "Generating Redis private key ($KEY_SIZE bits)..."
openssl genrsa -out redis.key $KEY_SIZE 2>/dev/null

# Set secure permissions on Redis private key
chmod 600 redis.key
print_success "Redis private key generated: redis.key"

# Generate Certificate Signing Request (CSR)
print_info "Generating Certificate Signing Request (CSR)..."
openssl req -new -key redis.key -out redis.csr \
    -subj "/C=$COUNTRY/ST=$STATE/L=$CITY/O=$ORG/CN=$CN_REDIS" \
    2>/dev/null

print_success "CSR generated: redis.csr"

# Create extensions file for SAN (Subject Alternative Names)
cat > redis.ext << EOF
subjectAltName = @alt_names
extendedKeyUsage = serverAuth, clientAuth

[alt_names]
DNS.1 = localhost
DNS.2 = redis
DNS.3 = *.redis.local
IP.1 = 127.0.0.1
IP.2 = ::1
EOF

print_info "Created SAN extensions file: redis.ext"

# Sign Redis certificate with CA
print_info "Signing Redis certificate with CA..."
openssl x509 -req -in redis.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
    -out redis.crt -days $VALIDITY_DAYS -extfile redis.ext \
    2>/dev/null

print_success "Redis certificate generated: redis.crt"

# Display Redis certificate info
print_info "Redis Certificate Details:"
openssl x509 -in redis.crt -noout -subject -issuer -dates

# Verify certificate chain
print_info "Verifying certificate chain..."
if openssl verify -CAfile ca.crt redis.crt &>/dev/null; then
    print_success "Certificate chain is valid"
else
    print_warning "Certificate verification failed"
fi

# Clean up temporary files
rm -f redis.csr redis.ext ca.srl
print_info "Cleaned up temporary files"

################################################################################
# Set Permissions
################################################################################

print_header "Setting File Permissions"

# Public certificates - readable by all
chmod 644 ca.crt redis.crt
print_success "Public certificates (ca.crt, redis.crt): 644 (readable)"

# Private keys - readable only by owner
chmod 600 ca.key redis.key
print_success "Private keys (ca.key, redis.key): 600 (owner only)"

################################################################################
# Summary
################################################################################

print_header "Certificate Generation Complete"

echo ""
echo "📁 Generated Files:"
echo "   - ca.crt          (CA certificate - public)"
echo "   - ca.key          (CA private key - SECRET)"
echo "   - redis.crt       (Redis certificate - public)"
echo "   - redis.key       (Redis private key - SECRET)"
echo ""
echo "🔒 Security:"
echo "   - Key Size:       $KEY_SIZE bits RSA"
echo "   - Valid For:      $VALIDITY_DAYS days"
echo "   - Algorithm:      SHA-256"
echo "   - Private Keys:   chmod 600 (owner only)"
echo ""
echo "⚠️  Important:"
echo "   - These are SELF-SIGNED certificates for DEVELOPMENT"
echo "   - For PRODUCTION, use Let's Encrypt or commercial CA"
echo "   - Keep ca.key and redis.key SECRET (already in .gitignore)"
echo "   - Certificates expire on: $(date -d "+$VALIDITY_DAYS days" +%Y-%m-%d)"
echo ""
echo "✅ Next Steps:"
echo "   1. Update .env with Redis password"
echo "   2. Start Redis: redis-server redis/redis.conf"
echo "   3. Test connection: npm run redis:cli"
echo ""

print_success "All done! 🎉"
