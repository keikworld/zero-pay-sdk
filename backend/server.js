const express = require('express');
const redis = require('redis');
const bodyParser = require('body-parser');
const cors = require('cors');
const rateLimit = require('express-rate-limit');

const app = express();
const PORT = process.env.PORT || 3000;

// Redis client
const redisClient = redis.createClient({
  url: process.env.REDIS_URL || 'redis://localhost:6379'
});

redisClient.on('error', (err) => console.error('Redis Client Error', err));

(async () => {
  await redisClient.connect();
  console.log('✅ Connected to Redis');
})();

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(express.json());

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100 // limit each IP to 100 requests per windowMs
});
app.use(limiter);

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'healthy', timestamp: Date.now() });
});

// POST /v1/enrollment/store
app.post('/v1/enrollment/store', async (req, res) => {
  try {
    const { user_uuid, factors, device_id, ttl_seconds = 86400 } = req.body;
    
    // Validation
    if (!user_uuid || !factors || !device_id) {
      return res.status(400).json({
        success: false,
        error: 'Missing required fields'
      });
    }
    
    if (Object.keys(factors).length < 2) {
      return res.status(400).json({
        success: false,
        error: 'At least 2 factors required'
      });
    }
    
    // Validate digest format (64 hex chars = 32 bytes)
    for (const digest of Object.values(factors)) {
      if (!/^[0-9a-f]{64}$/i.test(digest)) {
        return res.status(400).json({
          success: false,
          error: 'Invalid digest format'
        });
      }
    }
    
    // Store in Redis with TTL
    const enrollmentData = {
      user_uuid,
      factors,
      created_at: Date.now(),
      expires_at: Date.now() + (ttl_seconds * 1000),
      device_id
    };
    
    const key = `enrollment:${user_uuid}`;
    await redisClient.setEx(
      key,
      ttl_seconds,
      JSON.stringify(enrollmentData)
    );
    
    console.log(`✅ Stored enrollment for ${user_uuid}, TTL: ${ttl_seconds}s`);
    
    res.json({
      success: true,
      enrollment_id: user_uuid,
      expires_at: enrollmentData.expires_at,
      message: 'Enrollment stored successfully'
    });
    
  } catch (error) {
    console.error('Error storing enrollment:', error);
    res.status(500).json({
      success: false,
      error: 'Internal server error'
    });
  }
});

// GET /v1/enrollment/retrieve/:uuid
app.get('/v1/enrollment/retrieve/:uuid', async (req, res) => {
  try {
    const { uuid } = req.params;
    
    const key = `enrollment:${uuid}`;
    const data = await redisClient.get(key);
    
    if (!data) {
      return res.status(404).json({
        success: false,
        error: 'Enrollment not found or expired'
      });
    }
    
    const enrollmentData = JSON.parse(data);
    
    // Check if expired (defensive check)
    if (Date.now() > enrollmentData.expires_at) {
      await redisClient.del(key);
      return res.status(404).json({
        success: false,
        error: 'Enrollment expired'
      });
    }
    
    console.log(`✅ Retrieved enrollment for ${uuid}`);
    
    res.json({
      success: true,
      data: enrollmentData
    });
    
  } catch (error) {
    console.error('Error retrieving enrollment:', error);
    res.status(500).json({
      success: false,
      error: 'Internal server error'
    });
  }
});

// DELETE /v1/enrollment/delete/:uuid (GDPR)
app.delete('/v1/enrollment/delete/:uuid', async (req, res) => {
  try {
    const { uuid } = req.params;
    
    const key = `enrollment:${uuid}`;
    const deleted = await redisClient.del(key);
    
    if (deleted === 0) {
      return res.status(404).json({
        success: false,
        error: 'Enrollment not found'
      });
    }
    
    console.log(`✅ Deleted enrollment for ${uuid} (GDPR)`);
    
    res.json({
      success: true,
      message: 'Enrollment deleted successfully'
    });
    
  } catch (error) {
    console.error('Error deleting enrollment:', error);
    res.status(500).json({
      success: false,
      error: 'Internal server error'
    });
  }
});

// GET /v1/auth/nonce
app.get('/v1/auth/nonce', (req, res) => {
  const crypto = require('crypto');
  const nonce = crypto.randomBytes(32).toString('hex');
  const timestamp = Date.now();
  
  res.json({
    nonce,
    timestamp,
    valid_for: 60000 // 60 seconds
  });
});

// Start server
app.listen(PORT, () => {
  console.log(`🚀 ZeroPay Backend API running on port ${PORT}`);
  console.log(`   Health: http://localhost:${PORT}/health`);
  console.log(`   Enrollment: http://localhost:${PORT}/v1/enrollment/store`);
});

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('\n⏹️  Shutting down...');
  await redisClient.quit();
  process.exit(0);
});
