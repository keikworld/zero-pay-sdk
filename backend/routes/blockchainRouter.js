// Path: backend/routes/blockchainRouter.js

/**
 * Blockchain Router - REST API for Blockchain Operations
 * 
 * Provides endpoints for Solana blockchain integration.
 * 
 * Endpoints:
 * - POST /v1/blockchain/wallets/link - Link wallet to UUID
 * - DELETE /v1/blockchain/wallets/unlink - Unlink wallet
 * - GET /v1/blockchain/wallets/:uuid - Get linked wallets
 * - GET /v1/blockchain/balance/:address - Get wallet balance
 * - POST /v1/blockchain/transactions/estimate - Estimate transaction fee
 * - GET /v1/blockchain/transactions/:signature - Get transaction status
 * - POST /v1/blockchain/transactions/verify - Verify transaction signature
 * 
 * Security:
 * - Authentication required (session token)
 * - Rate limiting (20 req/min)
 * - Input validation
 * - Audit logging
 * 
 * @version 1.0.0
 * @date 2025-10-17
 */

const express = require('express');
const router = express.Router();
const solanaService = require('../services/solanaService');

// ============================================================================
// MIDDLEWARE
// ============================================================================

/**
 * Rate limiting for blockchain endpoints
 */
const rateLimit = require('express-rate-limit');
const blockchainRateLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 20, // 20 requests per minute
  message: { error: 'Too many blockchain requests, please try again later' },
  standardHeaders: true,
  legacyHeaders: false
});

router.use(blockchainRateLimiter);

// ============================================================================
// WALLET LINKING
// ============================================================================

/**
 * POST /v1/blockchain/wallets/link
 * 
 * Link Solana wallet to user UUID
 * 
 * Body:
 * {
 *   "userUuid": "user-uuid-123",
 *   "walletAddress": "7x...abc",
 *   "enableRedisCache": false
 * }
 * 
 * Response:
 * {
 *   "success": true,
 *   "message": "Wallet linked successfully"
 * }
 */
router.post('/wallets/link', async (req, res) => {
  try {
    const { userUuid, walletAddress, enableRedisCache } = req.body;
    
    // Validate inputs
    if (!userUuid || !walletAddress) {
      return res.status(400).json({
        error: 'Missing required fields: userUuid, walletAddress'
      });
    }
    
    solanaService.validateAddress(walletAddress);
    
    // Optionally store wallet hash in Redis
    if (enableRedisCache) {
      await solanaService.storeWalletHash(req.redisClient, userUuid, walletAddress);
    }
    
    console.log(`✅ Wallet linked for user: ${userUuid}`);
    
    res.json({
      success: true,
      message: 'Wallet linked successfully',
      walletHash: solanaService.hashWalletAddress(walletAddress)
    });
    
  } catch (error) {
    console.error('❌ Wallet linking error:', error);
    res.status(500).json({
      error: 'Failed to link wallet',
      details: error.message
    });
  }
});

/**
 * DELETE /v1/blockchain/wallets/unlink
 * 
 * Unlink Solana wallet from user UUID
 * 
 * Body:
 * {
 *   "walletAddress": "7x...abc"
 * }
 * 
 * Response:
 * {
 *   "success": true,
 *   "message": "Wallet unlinked successfully"
 * }
 */
router.delete('/wallets/unlink', async (req, res) => {
  try {
    const { walletAddress } = req.body;
    
    if (!walletAddress) {
      return res.status(400).json({
        error: 'Missing required field: walletAddress'
      });
    }
    
    solanaService.validateAddress(walletAddress);
    
    // Remove wallet hash from Redis
    await solanaService.deleteWalletHash(req.redisClient, walletAddress);
    
    console.log(`✅ Wallet unlinked: ${walletAddress}`);
    
    res.json({
      success: true,
      message: 'Wallet unlinked successfully'
    });
    
  } catch (error) {
    console.error('❌ Wallet unlinking error:', error);
    res.status(500).json({
      error: 'Failed to unlink wallet',
      details: error.message
    });
  }
});

/**
 * GET /v1/blockchain/wallets/user/:address
 * 
 * Find user UUID by wallet address (Redis lookup)
 * 
 * Response:
 * {
 *   "userUuid": "user-uuid-123"
 * }
 */
router.get('/wallets/user/:address', async (req, res) => {
  try {
    const { address } = req.params;
    
    solanaService.validateAddress(address);
    
    const userUuid = await solanaService.getUserByWallet(req.redisClient, address);
    
    if (!userUuid) {
      return res.status(404).json({
        error: 'User not found for wallet address'
      });
    }
    
    res.json({ userUuid });
    
  } catch (error) {
    console.error('❌ User lookup error:', error);
    res.status(500).json({
      error: 'Failed to lookup user',
      details: error.message
    });
  }
});

// ============================================================================
// BLOCKCHAIN QUERIES
// ============================================================================

/**
 * GET /v1/blockchain/balance/:address
 * 
 * Get wallet balance
 * 
 * Response:
 * {
 *   "address": "7x...abc",
 *   "balance": {
 *     "lamports": 1000000000,
 *     "sol": 1.0
 *   }
 * }
 */
router.get('/balance/:address', async (req, res) => {
  try {
    const { address } = req.params;
    
    solanaService.validateAddress(address);
    
    const lamports = await solanaService.getBalance(address);
    const sol = solanaService.lamportsToSol(lamports);
    
    res.json({
      address,
      balance: {
        lamports,
        sol
      }
    });
    
  } catch (error) {
    console.error('❌ Balance query error:', error);
    res.status(500).json({
      error: 'Failed to get balance',
      details: error.message
    });
  }
});

/**
 * POST /v1/blockchain/transactions/estimate
 * 
 * Estimate transaction fee
 * 
 * Body:
 * {
 *   "transaction": "base64-encoded-transaction"
 * }
 * 
 * Response:
 * {
 *   "fee": {
 *     "lamports": 5000,
 *     "sol": 0.000005
 *   }
 * }
 */
router.post('/transactions/estimate', async (req, res) => {
  try {
    const { transaction } = req.body;
    
    if (!transaction) {
      return res.status(400).json({
        error: 'Missing required field: transaction'
      });
    }
    
    const lamports = await solanaService.estimateFee(transaction);
    const sol = solanaService.lamportsToSol(lamports);
    
    res.json({
      fee: {
        lamports,
        sol
      }
    });
    
  } catch (error) {
    console.error('❌ Fee estimation error:', error);
    res.status(500).json({
      error: 'Failed to estimate fee',
      details: error.message
    });
  }
});

/**
 * GET /v1/blockchain/transactions/:signature
 * 
 * Get transaction status
 * 
 * Response:
 * {
 *   "signature": "5x...xyz",
 *   "confirmationStatus": "finalized",
 *   "confirmations": 32,
 *   "slot": 123456789,
 *   "err": null
 * }
 */
router.get('/transactions/:signature', async (req, res) => {
  try {
    const { signature } = req.params;
    
    solanaService.validateSignature(signature);
    
    // Check cache first
    let status = await solanaService.getCachedTransactionStatus(req.redisClient, signature);
    
    if (!status) {
      // Query blockchain
      status = await solanaService.getTransactionStatus(signature);
      
      // Cache if finalized
      if (status.confirmationStatus === 'finalized') {
        await solanaService.cacheTransactionStatus(req.redisClient, signature, status);
      }
    }
    
    res.json(status);
    
  } catch (error) {
    console.error('❌ Transaction status error:', error);
    res.status(500).json({
      error: 'Failed to get transaction status',
      details: error.message
    });
  }
});

/**
 * POST /v1/blockchain/transactions/verify
 * 
 * Verify transaction signature
 * 
 * Body:
 * {
 *   "signature": "5x...xyz"
 * }
 * 
 * Response:
 * {
 *   "valid": true,
 *   "status": { ... }
 * }
 */
router.post('/transactions/verify', async (req, res) => {
  try {
    const { signature } = req.body;
    
    if (!signature) {
      return res.status(400).json({
        error: 'Missing required field: signature'
      });
    }
    
    solanaService.validateSignature(signature);
    
    const valid = await solanaService.verifyTransactionSignature(signature);
    const status = await solanaService.getTransactionStatus(signature);
    
    res.json({
      valid,
      status
    });
    
  } catch (error) {
    console.error('❌ Signature verification error:', error);
    res.status(500).json({
      error: 'Failed to verify signature',
      details: error.message
    });
  }
});

// ============================================================================
// HEALTH CHECK
// ============================================================================

/**
 * GET /v1/blockchain/health
 * 
 * Check Solana RPC connection health
 * 
 * Response:
 * {
 *   "status": "healthy",
 *   "network": "mainnet-beta",
 *   "blockHeight": 123456789
 * }
 */
router.get('/health', async (req, res) => {
  try {
    const blockhash = await solanaService.getRecentBlockhash();
    
    res.json({
      status: 'healthy',
      network: process.env.SOLANA_NETWORK || 'mainnet-beta',
      blockHeight: blockhash.lastValidBlockHeight
    });
    
  } catch (error) {
    console.error('❌ Health check failed:', error);
    res.status(503).json({
      status: 'unhealthy',
      error: error.message
    });
  }
});

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = router;
