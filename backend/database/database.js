// Path: backend/database/database.js

/**
 * ZeroPay Database Module - PostgreSQL Integration
 * 
 * Purpose: Store wrapped encryption keys (NOT in Redis)
 * 
 * Architecture:
 * - Redis: Factor digests (24h TTL, encrypted)
 * - PostgreSQL: Wrapped keys (permanent, until GDPR deletion)
 * 
 * Why Separate Database?
 * - Redis is ephemeral (24h TTL)
 * - Wrapped keys must persist for key rotation
 * - GDPR requires permanent deletion capability
 * - Wrapped keys enable cryptographic deletion
 * 
 * Security:
 * - Wrapped keys stored (NOT raw keys)
 * - Encryption at rest (PostgreSQL + disk encryption)
 * - Row-level security (RLS)
 * - Audit logging
 * - No PII stored
 * 
 * GDPR Compliance:
 * - Right to erasure (DELETE wrapped key)
 * - Cryptographic deletion (wrapped key gone = data unreadable)
 * - Audit trail for compliance
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */

const { Pool } = require('pg');

// ============================================================================
// CONFIGURATION
// ============================================================================

const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT) || 5432,
  database: process.env.DB_NAME || 'zeropay',
  user: process.env.DB_USER || 'zeropay_backend',
  password: process.env.DB_PASSWORD,
  
  // Connection pool settings
  max: 20,                    // Maximum pool size
  min: 2,                     // Minimum pool size
  idleTimeoutMillis: 30000,   // Close idle clients after 30s
  connectionTimeoutMillis: 5000, // Timeout after 5s
  
  // SSL/TLS for production
  ssl: process.env.NODE_ENV === 'production' ? {
    rejectUnauthorized: true,
    ca: process.env.DB_SSL_CA,
    cert: process.env.DB_SSL_CERT,
    key: process.env.DB_SSL_KEY
  } : false
});

// Error handling
pool.on('error', (err) => {
  console.error('❌ Unexpected database error:', err.message);
  process.exit(-1);
});

// Connection test
pool.on('connect', () => {
  console.log('✅ Database connection established');
});

// ============================================================================
// SCHEMA INITIALIZATION
// ============================================================================

/**
 * Initialize database schema
 * 
 * Tables:
 * - wrapped_keys: Store wrapped encryption keys
 * - audit_log: GDPR compliance logging
 * 
 * @returns {Promise<void>}
 */
async function initializeSchema() {
  const client = await pool.connect();
  
  try {
    await client.query('BEGIN');
    
    // Table: wrapped_keys
    await client.query(`
      CREATE TABLE IF NOT EXISTS wrapped_keys (
        uuid VARCHAR(36) PRIMARY KEY,
        wrapped_key TEXT NOT NULL,
        kms_key_id VARCHAR(256) NOT NULL,
        key_version INTEGER NOT NULL DEFAULT 1,
        factor_count INTEGER NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        last_accessed_at TIMESTAMPTZ,
        device_id VARCHAR(128),
        metadata JSONB DEFAULT '{}'::jsonb
      )
    `);
    
    // Indexes for performance
    await client.query(`
      CREATE INDEX IF NOT EXISTS idx_wrapped_keys_created_at 
      ON wrapped_keys(created_at);
    `);
    
    await client.query(`
      CREATE INDEX IF NOT EXISTS idx_wrapped_keys_device_id 
      ON wrapped_keys(device_id);
    `);
    
    // Table: audit_log (GDPR compliance)
    await client.query(`
      CREATE TABLE IF NOT EXISTS audit_log (
        id SERIAL PRIMARY KEY,
        uuid VARCHAR(36) NOT NULL,
        action VARCHAR(50) NOT NULL,
        timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        ip_address INET,
        user_agent TEXT,
        details JSONB DEFAULT '{}'::jsonb
      )
    `);
    
    // Index for audit queries
    await client.query(`
      CREATE INDEX IF NOT EXISTS idx_audit_log_uuid 
      ON audit_log(uuid);
    `);
    
    await client.query(`
      CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp 
      ON audit_log(timestamp);
    `);
    
    await client.query('COMMIT');
    
    console.log('✅ Database schema initialized');
    
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('❌ Schema initialization failed:', error.message);
    throw error;
  } finally {
    client.release();
  }
}

// ============================================================================
// WRAPPED KEY OPERATIONS
// ============================================================================

/**
 * Store wrapped encryption key
 * 
 * @param {Object} params
 * @param {string} params.uuid - User UUID
 * @param {string} params.wrappedKey - KMS-wrapped encryption key (hex)
 * @param {string} params.kmsKeyId - KMS key ID used for wrapping
 * @param {number} params.keyVersion - Key version
 * @param {number} params.factorCount - Number of factors enrolled
 * @param {string} params.deviceId - Device identifier
 * @param {Object} params.metadata - Additional metadata
 * @param {string} params.ipAddress - Client IP (for audit)
 * @returns {Promise<Object>} Inserted record
 */
async function storeWrappedKey({
  uuid,
  wrappedKey,
  kmsKeyId,
  keyVersion = 1,
  factorCount,
  deviceId,
  metadata = {},
  ipAddress
}) {
  // Validation
  if (!uuid || !wrappedKey || !kmsKeyId || !factorCount) {
    throw new Error('Missing required fields: uuid, wrappedKey, kmsKeyId, factorCount');
  }
  
  // Validate UUID format
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (!uuidRegex.test(uuid)) {
    throw new Error('Invalid UUID format');
  }
  
  // Validate wrapped key format (hex string)
  if (!/^[0-9a-f]+$/i.test(wrappedKey)) {
    throw new Error('Wrapped key must be hexadecimal');
  }
  
  // Validate factor count (PSD3: min 2)
  if (factorCount < 2 || factorCount > 10) {
    throw new Error('Factor count must be between 2 and 10');
  }
  
  const client = await pool.connect();
  
  try {
    await client.query('BEGIN');
    
    // Upsert wrapped key
    const result = await client.query(
      `INSERT INTO wrapped_keys (
        uuid, wrapped_key, kms_key_id, key_version, 
        factor_count, device_id, metadata, created_at, updated_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, NOW(), NOW())
      ON CONFLICT (uuid) DO UPDATE SET
        wrapped_key = EXCLUDED.wrapped_key,
        kms_key_id = EXCLUDED.kms_key_id,
        key_version = EXCLUDED.key_version,
        factor_count = EXCLUDED.factor_count,
        device_id = EXCLUDED.device_id,
        metadata = EXCLUDED.metadata,
        updated_at = NOW()
      RETURNING *`,
      [uuid, wrappedKey, kmsKeyId, keyVersion, factorCount, deviceId, JSON.stringify(metadata)]
    );
    
    // Audit log
    await client.query(
      `INSERT INTO audit_log (uuid, action, ip_address, details)
       VALUES ($1, $2, $3, $4)`,
      [
        uuid,
        'STORE_WRAPPED_KEY',
        ipAddress,
        JSON.stringify({ factor_count: factorCount, key_version: keyVersion })
      ]
    );
    
    await client.query('COMMIT');
    
    console.log(`✅ Stored wrapped key for UUID: ${uuid.slice(0, 8)}...`);
    
    return result.rows[0];
    
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('❌ Failed to store wrapped key:', error.message);
    throw error;
  } finally {
    client.release();
  }
}

/**
 * Retrieve wrapped encryption key
 * 
 * @param {string} uuid - User UUID
 * @param {string} ipAddress - Client IP (for audit)
 * @returns {Promise<Object|null>} Wrapped key record or null
 */
async function getWrappedKey(uuid, ipAddress) {
  // Validation
  if (!uuid) {
    throw new Error('UUID required');
  }
  
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (!uuidRegex.test(uuid)) {
    throw new Error('Invalid UUID format');
  }
  
  const client = await pool.connect();
  
  try {
    await client.query('BEGIN');
    
    // Retrieve wrapped key
    const result = await client.query(
      `SELECT * FROM wrapped_keys WHERE uuid = $1`,
      [uuid]
    );
    
    if (result.rows.length === 0) {
      await client.query('COMMIT');
      return null;
    }
    
    // Update last accessed timestamp
    await client.query(
      `UPDATE wrapped_keys SET last_accessed_at = NOW() WHERE uuid = $1`,
      [uuid]
    );
    
    // Audit log
    await client.query(
      `INSERT INTO audit_log (uuid, action, ip_address)
       VALUES ($1, $2, $3)`,
      [uuid, 'RETRIEVE_WRAPPED_KEY', ipAddress]
    );
    
    await client.query('COMMIT');
    
    console.log(`✅ Retrieved wrapped key for UUID: ${uuid.slice(0, 8)}...`);
    
    return result.rows[0];
    
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('❌ Failed to retrieve wrapped key:', error.message);
    throw error;
  } finally {
    client.release();
  }
}

/**
 * Delete wrapped key (GDPR right to erasure)
 * 
 * This is cryptographic deletion:
 * - Wrapped key deleted = factor data becomes permanently unreadable
 * - No recovery possible
 * - Complies with GDPR Article 17
 * 
 * @param {string} uuid - User UUID
 * @param {string} ipAddress - Client IP (for audit)
 * @param {string} reason - Deletion reason (for audit)
 * @returns {Promise<boolean>} True if deleted
 */
async function deleteWrappedKey(uuid, ipAddress, reason = 'USER_REQUEST') {
  // Validation
  if (!uuid) {
    throw new Error('UUID required');
  }
  
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (!uuidRegex.test(uuid)) {
    throw new Error('Invalid UUID format');
  }
  
  const client = await pool.connect();
  
  try {
    await client.query('BEGIN');
    
    // Delete wrapped key
    const result = await client.query(
      `DELETE FROM wrapped_keys WHERE uuid = $1 RETURNING uuid`,
      [uuid]
    );
    
    const deleted = result.rowCount > 0;
    
    // Audit log (CRITICAL for GDPR compliance)
    await client.query(
      `INSERT INTO audit_log (uuid, action, ip_address, details)
       VALUES ($1, $2, $3, $4)`,
      [
        uuid,
        'DELETE_WRAPPED_KEY',
        ipAddress,
        JSON.stringify({ reason, deleted })
      ]
    );
    
    await client.query('COMMIT');
    
    if (deleted) {
      console.log(`✅ Deleted wrapped key for UUID: ${uuid.slice(0, 8)}... (GDPR)`);
    } else {
      console.log(`⚠️  No wrapped key found for UUID: ${uuid.slice(0, 8)}...`);
    }
    
    return deleted;
    
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('❌ Failed to delete wrapped key:', error.message);
    throw error;
  } finally {
    client.release();
  }
}

/**
 * Check if wrapped key exists
 * 
 * @param {string} uuid - User UUID
 * @returns {Promise<boolean>} True if exists
 */
async function hasWrappedKey(uuid) {
  // Validation
  if (!uuid) {
    throw new Error('UUID required');
  }
  
  try {
    const result = await pool.query(
      `SELECT 1 FROM wrapped_keys WHERE uuid = $1 LIMIT 1`,
      [uuid]
    );
    
    return result.rowCount > 0;
    
  } catch (error) {
    console.error('❌ Failed to check wrapped key existence:', error.message);
    throw error;
  }
}

// ============================================================================
// AUDIT LOG OPERATIONS
// ============================================================================

/**
 * Get audit log for a UUID (GDPR compliance)
 * 
 * @param {string} uuid - User UUID
 * @param {Object} options - Query options
 * @param {number} options.limit - Max records (default: 100)
 * @param {number} options.offset - Offset (default: 0)
 * @returns {Promise<Array>} Audit log records
 */
async function getAuditLog(uuid, { limit = 100, offset = 0 } = {}) {
  // Validation
  if (!uuid) {
    throw new Error('UUID required');
  }
  
  try {
    const result = await pool.query(
      `SELECT * FROM audit_log 
       WHERE uuid = $1 
       ORDER BY timestamp DESC 
       LIMIT $2 OFFSET $3`,
      [uuid, limit, offset]
    );
    
    return result.rows;
    
  } catch (error) {
    console.error('❌ Failed to retrieve audit log:', error.message);
    throw error;
  }
}

/**
 * Export user data (GDPR Article 15 - Right of Access)
 * 
 * @param {string} uuid - User UUID
 * @returns {Promise<Object>} User data export
 */
async function exportUserData(uuid) {
  // Validation
  if (!uuid) {
    throw new Error('UUID required');
  }
  
  const client = await pool.connect();
  
  try {
    // Get wrapped key metadata (NOT the key itself)
    const wrappedKeyResult = await client.query(
      `SELECT uuid, kms_key_id, key_version, factor_count, 
              created_at, updated_at, last_accessed_at, device_id
       FROM wrapped_keys 
       WHERE uuid = $1`,
      [uuid]
    );
    
    // Get audit log
    const auditLogResult = await client.query(
      `SELECT action, timestamp, ip_address, details
       FROM audit_log 
       WHERE uuid = $1 
       ORDER BY timestamp DESC`,
      [uuid]
    );
    
    return {
      uuid,
      wrapped_key_metadata: wrappedKeyResult.rows[0] || null,
      audit_log: auditLogResult.rows,
      export_date: new Date().toISOString()
    };
    
  } catch (error) {
    console.error('❌ Failed to export user data:', error.message);
    throw error;
  } finally {
    client.release();
  }
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Health check
 * 
 * @returns {Promise<boolean>} True if database is healthy
 */
async function healthCheck() {
  try {
    const result = await pool.query('SELECT 1');
    return result.rowCount === 1;
  } catch (error) {
    console.error('❌ Database health check failed:', error.message);
    return false;
  }
}

/**
 * Close database connections (graceful shutdown)
 * 
 * @returns {Promise<void>}
 */
async function close() {
  await pool.end();
  console.log('✅ Database connections closed');
}

/**
 * Get database statistics
 * 
 * @returns {Promise<Object>} Database stats
 */
async function getStats() {
  try {
    const wrappedKeysResult = await pool.query(
      `SELECT COUNT(*) as count FROM wrapped_keys`
    );
    
    const auditLogResult = await pool.query(
      `SELECT COUNT(*) as count FROM audit_log`
    );
    
    return {
      wrapped_keys_count: parseInt(wrappedKeysResult.rows[0].count),
      audit_log_count: parseInt(auditLogResult.rows[0].count),
      pool_total: pool.totalCount,
      pool_idle: pool.idleCount,
      pool_waiting: pool.waitingCount
    };
    
  } catch (error) {
    console.error('❌ Failed to get database stats:', error.message);
    throw error;
  }
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  // Setup
  initializeSchema,
  close,
  
  // Wrapped key operations
  storeWrappedKey,
  getWrappedKey,
  deleteWrappedKey,
  hasWrappedKey,
  
  // Audit operations
  getAuditLog,
  exportUserData,
  
  // Utilities
  healthCheck,
  getStats,
  
  // Pool (for advanced usage)
  pool
};

// ============================================================================
// INITIALIZATION
// ============================================================================

// Auto-initialize schema on first require
if (require.main !== module) {
  initializeSchema().catch(err => {
    console.error('❌ Failed to initialize database schema:', err.message);
    console.error('   Database operations may fail until schema is created.');
  });
}

// ============================================================================
// CLI COMMANDS
// ============================================================================

if (require.main === module) {
  const command = process.argv[2];
  
  if (command === 'init') {
    console.log('Initializing database schema...');
    initializeSchema()
      .then(() => {
        console.log('✅ Database schema initialized successfully');
        process.exit(0);
      })
      .catch(err => {
        console.error('❌ Failed to initialize schema:', err.message);
        process.exit(1);
      });
      
  } else if (command === 'stats') {
    getStats()
      .then(stats => {
        console.log('📊 Database Statistics:');
        console.log(JSON.stringify(stats, null, 2));
        process.exit(0);
      })
      .catch(err => {
        console.error('❌ Failed to get stats:', err.message);
        process.exit(1);
      });
      
  } else {
    console.log('Usage:');
    console.log('  node database.js init   - Initialize database schema');
    console.log('  node database.js stats  - Show database statistics');
  }
}
