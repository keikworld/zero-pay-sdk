#!/bin/bash

# Test Verification Script for ZeroPay SDK
# Verifies all test files are present and dependencies are available
# Version: 1.0.0
# Date: 2025-10-18

set -e

echo "======================================================================"
echo "  ZeroPay SDK - Test Verification"
echo "======================================================================"
echo ""

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0

check_file() {
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} Found: $1"
        PASSED_CHECKS=$((PASSED_CHECKS + 1))
        return 0
    else
        echo -e "${RED}✗${NC} Missing: $1"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
        return 1
    fi
}

check_command() {
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    if command -v "$1" &> /dev/null; then
        echo -e "${GREEN}✓${NC} Command available: $1"
        PASSED_CHECKS=$((PASSED_CHECKS + 1))
        return 0
    else
        echo -e "${RED}✗${NC} Command missing: $1"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
        return 1
    fi
}

echo "Step 1: Checking Implementation Files"
echo "----------------------------------------------------------------------"
check_file "sdk/src/commonMain/kotlin/com/zeropay/sdk/config/IntegrationConfig.kt"
check_file "sdk/src/commonMain/kotlin/com/zeropay/sdk/integration/BackendIntegration.kt"
check_file "enrollment/src/androidMain/kotlin/com/zeropay/enrollment/EnrollmentManager.kt"
check_file "merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/VerificationManager.kt"
check_file "sdk/src/commonMain/kotlin/com/zeropay/sdk/api/EnrollmentClient.kt"
check_file "sdk/src/commonMain/kotlin/com/zeropay/sdk/api/VerificationClient.kt"
echo ""

echo "Step 2: Checking Unit Test Files"
echo "----------------------------------------------------------------------"
check_file "sdk/src/test/kotlin/com/zeropay/sdk/config/IntegrationConfigTest.kt"
check_file "sdk/src/test/kotlin/com/zeropay/sdk/integration/BackendIntegrationTest.kt"
echo ""

echo "Step 3: Checking Integration Test Files"
echo "----------------------------------------------------------------------"
check_file "backend/tests/redis-connection.test.js"
check_file "backend/tests/e2e/complete-flow.test.js"
echo ""

echo "Step 4: Checking Documentation Files"
echo "----------------------------------------------------------------------"
check_file "PHASE_3_COMPLETION_SUMMARY.md"
check_file "TESTING_GUIDE.md"
check_file "PHASE_1_2_TEST_COVERAGE.md"
echo ""

echo "Step 5: Checking Build Tools"
echo "----------------------------------------------------------------------"
check_command "npm"
check_command "node"
if [ -f "./gradlew" ]; then
    echo -e "${GREEN}✓${NC} Gradle wrapper found: ./gradlew"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
else
    echo -e "${RED}✗${NC} Gradle wrapper missing: ./gradlew"
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
fi
echo ""

echo "Step 6: Checking Backend Dependencies"
echo "----------------------------------------------------------------------"
if [ -d "backend/node_modules" ]; then
    echo -e "${GREEN}✓${NC} Backend dependencies installed"
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
else
    echo -e "${YELLOW}⚠${NC} Backend dependencies not installed (run: cd backend && npm install)"
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
fi
echo ""

echo "======================================================================"
echo "  Test Verification Summary"
echo "======================================================================"
echo ""
echo "Total Checks:  $TOTAL_CHECKS"
echo -e "Passed:        ${GREEN}$PASSED_CHECKS${NC}"
if [ $FAILED_CHECKS -gt 0 ]; then
    echo -e "Failed:        ${RED}$FAILED_CHECKS${NC}"
else
    echo -e "Failed:        ${GREEN}$FAILED_CHECKS${NC}"
fi
echo ""

# Test execution commands reference
echo "======================================================================"
echo "  Quick Test Execution Commands"
echo "======================================================================"
echo ""
echo "Backend E2E Tests:"
echo "  cd backend && npm test -- tests/e2e/complete-flow.test.js"
echo ""
echo "Kotlin Unit Tests (All):"
echo "  ./gradlew test"
echo ""
echo "Kotlin Unit Tests (Integration):"
echo "  ./gradlew :sdk:test --tests \"com.zeropay.sdk.integration.*\""
echo ""
echo "Kotlin Unit Tests (Config):"
echo "  ./gradlew :sdk:test --tests \"com.zeropay.sdk.config.*\""
echo ""
echo "Run All Tests:"
echo "  ./gradlew test && cd backend && npm test"
echo ""

# Exit with appropriate code
if [ $FAILED_CHECKS -gt 0 ]; then
    echo -e "${RED}VERIFICATION FAILED${NC} - Some checks did not pass"
    echo ""
    exit 1
else
    echo -e "${GREEN}VERIFICATION PASSED${NC} - All checks passed!"
    echo ""
    exit 0
fi
