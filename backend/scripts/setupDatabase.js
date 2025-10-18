#!/usr/bin/env node

/**
 * Database Setup Script
 *
 * Initializes PostgreSQL database with schema and creates necessary tables.
 *
 * Usage:
 *   npm run db:setup
 *
 * Environment Variables:
 *   DATABASE_URL - PostgreSQL connection string
 *
 * @version 1.0.0
 */

const { Client } = require('pg');
const fs = require('fs');
const path = require('path');
require('dotenv').config();

const DATABASE_URL = process.env.DATABASE_URL;

if (!DATABASE_URL) {
  console.error('❌ ERROR: DATABASE_URL environment variable not set');
  console.error('   Example: postgresql://user:password@localhost:5432/zeropay');
  process.exit(1);
}

async function setupDatabase() {
  const client = new Client({
    connectionString: DATABASE_URL,
    ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false
  });

  try {
    console.log('📦 Connecting to PostgreSQL...');
    await client.connect();
    console.log('✅ Connected to database');

    // Read schema file
    const schemaPath = path.join(__dirname, '../database/schemas/schema.sql');
    console.log(`📄 Reading schema from: ${schemaPath}`);

    if (!fs.existsSync(schemaPath)) {
      throw new Error(`Schema file not found: ${schemaPath}`);
    }

    const schema = fs.readFileSync(schemaPath, 'utf8');

    // Execute schema
    console.log('🔨 Executing schema...');
    await client.query(schema);
    console.log('✅ Schema created successfully');

    // Verify tables
    console.log('🔍 Verifying tables...');
    const tablesResult = await client.query(`
      SELECT table_name
      FROM information_schema.tables
      WHERE table_schema = 'public'
      AND table_type = 'BASE TABLE'
      ORDER BY table_name
    `);

    console.log('📋 Created tables:');
    tablesResult.rows.forEach(row => {
      console.log(`   ✓ ${row.table_name}`);
    });

    // Verify views
    const viewsResult = await client.query(`
      SELECT table_name
      FROM information_schema.views
      WHERE table_schema = 'public'
      ORDER BY table_name
    `);

    if (viewsResult.rows.length > 0) {
      console.log('📋 Created views:');
      viewsResult.rows.forEach(row => {
        console.log(`   ✓ ${row.table_name}`);
      });
    }

    console.log('\n✅ Database setup completed successfully!');
    console.log('\n📝 Next steps:');
    console.log('   1. Update .env with your database credentials');
    console.log('   2. Run: npm run dev');
    console.log('   3. Test enrollment endpoint: POST http://localhost:3000/api/v1/enrollment/store');

  } catch (error) {
    console.error('\n❌ Database setup failed:');
    console.error(`   Error: ${error.message}`);
    if (error.stack) {
      console.error(`\n   Stack trace:\n${error.stack}`);
    }
    process.exit(1);
  } finally {
    await client.end();
    console.log('\n🔌 Database connection closed');
  }
}

// Run setup
console.log('🚀 ZeroPay Database Setup\n');
console.log('==================================================');
console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
console.log(`Database: ${DATABASE_URL.replace(/:[^:@]+@/, ':****@')}`); // Hide password
console.log('==================================================\n');

setupDatabase().catch(error => {
  console.error('Fatal error:', error);
  process.exit(1);
});
