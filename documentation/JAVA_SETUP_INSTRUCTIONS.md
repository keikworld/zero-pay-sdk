# Java Setup Instructions for WSL - ZeroPay SDK

**Purpose:** Enable Kotlin/Gradle tests in WSL environment
**Requirement:** OpenJDK 17+
**Time:** ~5 minutes

---

## 🚀 Quick Setup (Recommended)

### Option 1: Automated Script

Run the provided setup script:

```bash
cd /mnt/c/Users/USUARIO/StudioProjects/zero-pay-sdk/zeropay-android

# Make executable
chmod +x setup-java-wsl.sh

# Run (will prompt for sudo password)
bash setup-java-wsl.sh
```

**What it does:**
1. Updates package list
2. Installs OpenJDK 17
3. Configures JAVA_HOME
4. Updates PATH
5. Tests Gradle compatibility

**Expected output:**
```
✓ Java Setup Complete!
Java version: openjdk version "17.x.x"
JAVA_HOME: /usr/lib/jvm/java-17-openjdk-amd64
```

---

### Option 2: Manual Setup

If you prefer manual installation:

#### Step 1: Update Package List

```bash
sudo apt update
```

#### Step 2: Install OpenJDK 17

```bash
sudo apt install -y openjdk-17-jdk
```

**Alternative versions:**
- OpenJDK 11: `sudo apt install openjdk-11-jdk`
- OpenJDK 21: `sudo apt install openjdk-21-jdk`

**Note:** Gradle requires Java 11 minimum, but Java 17 is recommended.

#### Step 3: Verify Installation

```bash
java -version
javac -version
```

**Expected output:**
```
openjdk version "17.0.x" 2024-xx-xx
OpenJDK Runtime Environment (build 17.0.x)
OpenJDK 64-Bit Server VM (build 17.0.x)
```

#### Step 4: Find JAVA_HOME Path

```bash
readlink -f $(which java)
```

**Example output:**
```
/usr/lib/jvm/java-17-openjdk-amd64/bin/java
```

**JAVA_HOME is:** `/usr/lib/jvm/java-17-openjdk-amd64`

#### Step 5: Configure Environment Variables

**Option A: Add to ~/.bashrc (Permanent)**

```bash
nano ~/.bashrc
```

Add at the end:

```bash
# Java Configuration (ZeroPay SDK)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

Save and reload:

```bash
source ~/.bashrc
```

**Option B: Current Session Only (Temporary)**

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

**Note:** This only works for current terminal session.

#### Step 6: Verify Configuration

```bash
echo $JAVA_HOME
java -version
```

**Expected:**
```
/usr/lib/jvm/java-17-openjdk-amd64
openjdk version "17.0.x"
```

---

## ✅ Verification Tests

### Test 1: Gradle Wrapper

```bash
cd /mnt/c/Users/USUARIO/StudioProjects/zero-pay-sdk/zeropay-android

# Make executable
chmod +x ./gradlew

# Test Gradle
./gradlew --version
```

**Expected output:**
```
Gradle 8.x

Build time:   2024-xx-xx
Revision:     <hash>

Kotlin:       1.9.x
Groovy:       3.x
Ant:          Apache Ant(TM) version 1.10.x
JVM:          17.0.x (Oracle Corporation)
OS:           Linux 6.6.x amd64
```

**✅ If you see JVM version 17.x, Java is configured correctly!**

---

### Test 2: Build Test Classes

```bash
./gradlew :sdk:testClasses --no-daemon
```

**Expected:**
- No errors
- BUILD SUCCESSFUL message

**If successful:**
```
BUILD SUCCESSFUL in 30s
15 actionable tasks: 15 executed
```

---

### Test 3: Run Unit Tests

```bash
./gradlew :sdk:test --tests "com.zeropay.sdk.config.IntegrationConfigTest" --no-daemon
```

**Expected:**
```
com.zeropay.sdk.config.IntegrationConfigTest > test default configuration is valid PASSED
com.zeropay.sdk.config.IntegrationConfigTest > test production configuration PASSED
...

BUILD SUCCESSFUL
20 tests completed, 20 passed
```

---

### Test 4: Run Backend Integration Tests

```bash
./gradlew :sdk:test --tests "com.zeropay.sdk.integration.BackendIntegrationTest" --no-daemon
```

**Expected:**
```
com.zeropay.sdk.integration.BackendIntegrationTest > test API_ONLY strategy succeeds PASSED
com.zeropay.sdk.integration.BackendIntegrationTest > test circuit breaker opens PASSED
...

BUILD SUCCESSFUL
25 tests completed, 25 passed
```

---

## 🐛 Troubleshooting

### Issue 1: "JAVA_HOME is not set"

**Symptom:**
```
ERROR: JAVA_HOME is not set and no 'java' command could be found
```

**Solution:**
```bash
# Check if Java is installed
java -version

# If installed, set JAVA_HOME
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
echo $JAVA_HOME

# Add to ~/.bashrc for persistence
echo "export JAVA_HOME=$JAVA_HOME" >> ~/.bashrc
echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.bashrc
source ~/.bashrc
```

---

### Issue 2: "Permission denied" on ./gradlew

**Symptom:**
```
bash: ./gradlew: Permission denied
```

**Solution:**
```bash
chmod +x ./gradlew
```

---

### Issue 3: Gradle Daemon Issues

**Symptom:**
```
Gradle daemon disappeared unexpectedly
```

**Solution:**
```bash
# Stop all Gradle daemons
./gradlew --stop

# Run without daemon
./gradlew :sdk:test --no-daemon
```

---

### Issue 4: Wrong Java Version

**Symptom:**
```
Gradle requires Java 11 or later. Current: Java 8
```

**Solution:**
```bash
# Check all installed Java versions
update-java-alternatives --list

# Switch to Java 17
sudo update-java-alternatives --set java-1.17.0-openjdk-amd64

# Or set manually
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

---

### Issue 5: "Unable to find method"

**Symptom:**
```
Could not determine java version from '17.0.x'
```

**Solution:**
```bash
# Update Gradle wrapper
./gradlew wrapper --gradle-version=8.5

# Or use newer Java
sudo apt install openjdk-21-jdk
```

---

## 📋 Quick Reference

### Environment Variables

```bash
# Check JAVA_HOME
echo $JAVA_HOME

# Check Java version
java -version

# Check PATH includes Java
echo $PATH | grep -o '[^:]*java[^:]*'
```

### Gradle Commands

```bash
# Check Gradle version
./gradlew --version

# List all tasks
./gradlew tasks

# Run all SDK tests
./gradlew :sdk:test

# Run specific test
./gradlew :sdk:test --tests "com.zeropay.sdk.config.*"

# Build without tests
./gradlew build -x test

# Clean build
./gradlew clean build
```

### Common Test Commands

```bash
# Integration config tests
./gradlew :sdk:test --tests "com.zeropay.sdk.config.*"

# Backend integration tests
./gradlew :sdk:test --tests "com.zeropay.sdk.integration.*"

# All SDK tests
./gradlew :sdk:test

# All module tests
./gradlew test

# Generate coverage report
./gradlew jacocoTestReport
```

---

## 🎯 Success Checklist

After setup, verify all of these:

- [ ] `java -version` shows Java 17+
- [ ] `echo $JAVA_HOME` shows correct path
- [ ] `./gradlew --version` shows Gradle info with JVM 17+
- [ ] `./gradlew :sdk:testClasses` builds without errors
- [ ] `./gradlew :sdk:test --tests "com.zeropay.sdk.config.*"` passes 20+ tests
- [ ] `./gradlew :sdk:test --tests "com.zeropay.sdk.integration.*"` passes 25+ tests

**If all checked:** ✅ Java is properly configured!

---

## 🔄 Alternative: Use Windows Java (if available)

If you have Java installed on Windows, you can use it from WSL:

### Option 1: Windows Java Path

```bash
# Find Windows Java
cmd.exe /c "where java"

# Example output: C:\Program Files\Java\jdk-17\bin\java.exe

# Convert to WSL path
/mnt/c/Program Files/Java/jdk-17/bin/java.exe -version

# Set JAVA_HOME (Windows path in WSL)
export JAVA_HOME="/mnt/c/Program Files/Java/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
```

**Note:** May be slower than native WSL Java.

---

## 📚 Additional Resources

**OpenJDK Documentation:**
- https://openjdk.org/install/

**Gradle Documentation:**
- https://docs.gradle.org/current/userguide/installation.html

**WSL Java Setup:**
- https://learn.microsoft.com/en-us/windows/wsl/tutorials/wsl-java

---

## 🎉 Next Steps After Setup

Once Java is configured:

1. **Run All Tests**
   ```bash
   ./gradlew test
   ```

2. **Generate Coverage Report**
   ```bash
   ./gradlew jacocoTestReport
   # Report: build/reports/jacoco/test/html/index.html
   ```

3. **Build APKs**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Verify CI/CD**
   ```bash
   # CI/CD will use GitHub Actions' Java setup
   # No local setup needed for CI/CD
   ```

---

**Setup Status:** Pending manual execution
**Estimated Time:** 5 minutes
**Difficulty:** Easy (automated script provided)

---

*Run `bash setup-java-wsl.sh` to get started!*
