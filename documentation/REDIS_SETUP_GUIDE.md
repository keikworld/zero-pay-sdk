# Redis Setup Guide for ZeroPay

**Quick setup instructions for Redis cache layer**

---

## 🎯 Quick Start (Development)

### Option 1: Simple Redis (No TLS)

Perfect for local development and testing.

```bash
# Install Redis (Ubuntu/WSL)
sudo apt update
sudo apt install redis-server

# Start Redis on default port
redis-server --port 6379

# Test connection
redis-cli ping
# Should return: PONG
```

**Update .env:**
```bash
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=  # Leave empty for development
REDIS_USERNAME=default
```

**Run tests:**
```bash
cd backend
npm test -- tests/redis-connection.test.js
```

---

## 🔒 Production Setup (With TLS)

### Step 1: Generate TLS Certificates

```bash
cd backend
npm run generate:certs
```

This creates:
- `redis/tls/ca.crt` - Certificate Authority
- `redis/tls/redis.crt` - Server certificate
- `redis/tls/redis.key` - Server private key
- `redis/tls/client.crt` - Client certificate (optional)
- `redis/tls/client.key` - Client private key (optional)

### Step 2: Configure Redis Password

Generate a secure password:
```bash
openssl rand -base64 32
```

Update `redis/redis.config`:
```conf
requirepass YOUR_GENERATED_PASSWORD
```

### Step 3: Start Redis with TLS

```bash
npm run redis:start
```

Redis will start on port **6380** with TLS enabled.

### Step 4: Update .env

```bash
REDIS_HOST=localhost
REDIS_PORT=6380
REDIS_PASSWORD=YOUR_GENERATED_PASSWORD
REDIS_USERNAME=zeropay-backend
REDIS_CA_CERT=./redis/tls/ca.crt
REDIS_CLIENT_CERT=./redis/tls/client.crt
REDIS_CLIENT_KEY=./redis/tls/client.key
```

### Step 5: Test TLS Connection

```bash
# Using redis-cli
npm run redis:cli

# Or manually
redis-cli --tls \
  --cert redis/tls/redis.crt \
  --key redis/tls/redis.key \
  --cacert redis/tls/ca.crt \
  -p 6380 \
  --user zeropay-backend \
  --askpass

# Enter password when prompted
```

### Step 6: Run Tests

```bash
npm test -- tests/redis-connection.test.js
```

---

## 🐳 Docker Setup (Alternative)

### Using Docker Compose

Create `docker-compose.yml`:
```yaml
version: '3.8'
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --requirepass YOUR_PASSWORD
    volumes:
      - redis-data:/data

volumes:
  redis-data:
```

Start:
```bash
docker-compose up -d
```

---

## ✅ Verification Checklist

After setup, verify:

- [ ] Redis is running
  ```bash
  redis-cli ping  # or npm run redis:cli (for TLS)
  ```

- [ ] Authentication works
  ```bash
  redis-cli -a YOUR_PASSWORD ping
  ```

- [ ] Backend can connect
  ```bash
  npm test -- tests/redis-connection.test.js
  ```

- [ ] SET/GET operations work
  ```bash
  redis-cli -a YOUR_PASSWORD
  SET test "hello"
  GET test
  DEL test
  ```

- [ ] TTL works
  ```bash
  redis-cli -a YOUR_PASSWORD
  SETEX test 5 "expires"
  TTL test
  # Wait 5 seconds
  GET test  # Should be nil
  ```

---

## 🔧 Configuration Details

### Redis Configuration (redis/redis.config)

Key settings:
- **Port:** 6380 (TLS), 0 (non-TLS disabled)
- **Max Memory:** 2GB
- **Eviction:** allkeys-lru (least recently used)
- **Max Clients:** 1000
- **Timeout:** 300s (5 minutes)
- **TLS:** Required for production
- **ACL:** Users defined in users.acl

### ACL Users (redis/users.acl)

Default users:
1. `admin` - Full access (for maintenance)
2. `zeropay-backend` - Limited access (for application)
3. `zeropay-readonly` - Read-only access (for monitoring)

---

## 🚨 Troubleshooting

### Redis won't start

**Check if port is already in use:**
```bash
lsof -i :6380  # or :6379
```

**Kill existing Redis:**
```bash
pkill redis-server
```

**Check logs:**
```bash
tail -f redis/redis.log
```

### TLS certificate errors

**Regenerate certificates:**
```bash
cd redis
rm -rf tls/*
./generate-tls-certs.sh
```

**Check certificate validity:**
```bash
openssl x509 -in redis/tls/redis.crt -text -noout
```

### Authentication errors

**Verify password in config:**
```bash
grep requirepass redis/redis.config
```

**Test with correct password:**
```bash
redis-cli -a YOUR_PASSWORD ping
```

### Connection timeouts

**Check Redis is listening:**
```bash
netstat -tuln | grep 6380  # or 6379
```

**Test network connectivity:**
```bash
telnet localhost 6380
```

### Memory issues

**Check current memory usage:**
```bash
redis-cli -a YOUR_PASSWORD INFO memory
```

**Clear all data (DANGER!):**
```bash
redis-cli -a YOUR_PASSWORD FLUSHALL  # Only if you have admin access
```

---

## 📊 Monitoring Commands

### Check server info
```bash
redis-cli -a YOUR_PASSWORD INFO
```

### Monitor commands in real-time
```bash
redis-cli -a YOUR_PASSWORD MONITOR
```

### Check connected clients
```bash
redis-cli -a YOUR_PASSWORD CLIENT LIST
```

### Check key count
```bash
redis-cli -a YOUR_PASSWORD DBSIZE
```

### Check memory usage
```bash
redis-cli -a YOUR_PASSWORD INFO memory
```

### Check slowlog
```bash
redis-cli -a YOUR_PASSWORD SLOWLOG GET 10
```

---

## 🧪 Testing Redis

### Basic operations
```bash
redis-cli -a YOUR_PASSWORD

# Strings
SET mykey "Hello"
GET mykey
DEL mykey

# Hashes (used for factors)
HSET user:123:factors PIN "digest123"
HSET user:123:factors PATTERN "digest456"
HGETALL user:123:factors
HLEN user:123:factors

# TTL
SETEX temp 10 "expires in 10s"
TTL temp
```

### Run test suite
```bash
cd backend

# Test Redis connection
npm test -- tests/redis-connection.test.js

# Test enrollment (uses Redis)
npm test -- tests/integration/enrollment.test.js

# Test verification (uses Redis)
npm test -- tests/integration/verification.test.js
```

---

## 🔐 Security Best Practices

### Development
- ✅ Use simple password or no password
- ✅ Bind to localhost only
- ✅ No TLS required (faster testing)

### Staging
- ✅ Use strong password (32+ chars)
- ✅ Enable TLS
- ✅ Use ACL for role separation
- ✅ Monitor connections

### Production
- ✅ Use AWS ElastiCache or Redis Cloud
- ✅ Enable TLS 1.3
- ✅ Require client certificates
- ✅ Enable encryption at rest
- ✅ Regular backups (if persistence enabled)
- ✅ Network isolation (VPC)
- ✅ Connection pooling
- ✅ Monitor memory and CPU

---

## 📚 Additional Resources

- [Redis Official Docs](https://redis.io/docs/)
- [Redis Security](https://redis.io/docs/management/security/)
- [Redis TLS](https://redis.io/docs/management/security/encryption/)
- [Redis ACL](https://redis.io/docs/management/security/acl/)

---

## ✅ Production Checklist

Before going to production:

- [ ] TLS 1.3 enabled
- [ ] Strong password set (32+ chars)
- [ ] ACL configured
- [ ] Client certificates required
- [ ] Dangerous commands disabled (FLUSHDB, KEYS, etc.)
- [ ] maxmemory policy set
- [ ] Timeout configured
- [ ] Logging enabled
- [ ] Monitoring set up
- [ ] Backup strategy (if persistence enabled)
- [ ] Network firewall rules
- [ ] Connection pooling configured
- [ ] Rate limiting in place

---

**Status:** Ready for Development & Production
**Next:** Start Redis → Run Tests → Start Backend

---

*Generated: 2025-10-18*
*Version: 1.0.0*
