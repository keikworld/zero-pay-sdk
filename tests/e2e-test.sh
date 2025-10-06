#!/bin/bash

echo "🧪 ZeroPay E2E Test"
echo "=================="

# 1. Start backend
echo "Starting backend..."
cd backend
npm start &
BACKEND_PID=$!
sleep 3

# 2. Check backend health
echo "Checking backend health..."
curl http://localhost:3000/health

# 3. Test enrollment endpoint
echo ""
echo "Testing enrollment endpoint..."
curl -X POST http://localhost:3000/v1/enrollment/store \
  -H "Content-Type: application/json" \
  -d '{
    "user_uuid": "test-uuid-123",
    "factors": {
      "PIN": "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
      "PATTERN": "fedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321"
    },
    "device_id": "test-device-123",
    "ttl_seconds": 86400
  }'

# 4. Test retrieval
echo ""
echo "Testing retrieval endpoint..."
curl http://localhost:3000/v1/enrollment/retrieve/test-uuid-123

# 5. Test deletion
echo ""
echo "Testing deletion endpoint..."
curl -X DELETE http://localhost:3000/v1/enrollment/delete/test-uuid-123

# Cleanup
echo ""
echo "Cleaning up..."
kill $BACKEND_PID

echo "✅ E2E Test Complete"
