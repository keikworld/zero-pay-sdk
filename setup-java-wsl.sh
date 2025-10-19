#!/bin/bash

# Java Setup Script for WSL (ZeroPay SDK)
# This script installs OpenJDK 17 and configures JAVA_HOME
# Run with: bash setup-java-wsl.sh

set -e

echo "======================================================================"
echo "  ZeroPay SDK - Java Setup for WSL"
echo "======================================================================"
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Step 1: Updating package list...${NC}"
sudo apt update

echo ""
echo -e "${BLUE}Step 2: Installing OpenJDK 17...${NC}"
sudo apt install -y openjdk-17-jdk

echo ""
echo -e "${BLUE}Step 3: Verifying installation...${NC}"
java -version
javac -version

echo ""
echo -e "${BLUE}Step 4: Locating JAVA_HOME...${NC}"
JAVA_HOME_PATH=$(dirname $(dirname $(readlink -f $(which java))))
echo "JAVA_HOME: $JAVA_HOME_PATH"

echo ""
echo -e "${BLUE}Step 5: Configuring environment variables...${NC}"

# Add to .bashrc if not already present
if ! grep -q "JAVA_HOME" ~/.bashrc; then
    echo "" >> ~/.bashrc
    echo "# Java Configuration (ZeroPay SDK)" >> ~/.bashrc
    echo "export JAVA_HOME=$JAVA_HOME_PATH" >> ~/.bashrc
    echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.bashrc
    echo -e "${GREEN}✓ Added JAVA_HOME to ~/.bashrc${NC}"
else
    echo -e "${YELLOW}⚠ JAVA_HOME already configured in ~/.bashrc${NC}"
fi

# Add to .profile if exists
if [ -f ~/.profile ]; then
    if ! grep -q "JAVA_HOME" ~/.profile; then
        echo "" >> ~/.profile
        echo "# Java Configuration (ZeroPay SDK)" >> ~/.profile
        echo "export JAVA_HOME=$JAVA_HOME_PATH" >> ~/.profile
        echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.profile
        echo -e "${GREEN}✓ Added JAVA_HOME to ~/.profile${NC}"
    fi
fi

# Set for current session
export JAVA_HOME=$JAVA_HOME_PATH
export PATH=$JAVA_HOME/bin:$PATH

echo ""
echo -e "${BLUE}Step 6: Verifying Gradle compatibility...${NC}"
cd "$(dirname "$0")"
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    ./gradlew --version || true
    echo -e "${GREEN}✓ Gradle wrapper found and tested${NC}"
else
    echo -e "${YELLOW}⚠ Gradle wrapper not found in current directory${NC}"
fi

echo ""
echo "======================================================================"
echo -e "${GREEN}  Java Setup Complete!${NC}"
echo "======================================================================"
echo ""
echo "Current Java version:"
java -version
echo ""
echo "Environment variables:"
echo "  JAVA_HOME: $JAVA_HOME"
echo "  PATH: (includes \$JAVA_HOME/bin)"
echo ""
echo -e "${YELLOW}IMPORTANT:${NC} To use Java in new terminal sessions, run:"
echo "  source ~/.bashrc"
echo ""
echo "Or restart your terminal."
echo ""
echo "Test with:"
echo "  ./gradlew :sdk:test --tests \"com.zeropay.sdk.config.*\""
echo ""
