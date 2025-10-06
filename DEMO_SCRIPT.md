# 🎬 ZeroPay Investor Demo Script

**Duration**: 10 minutes  
**Objective**: Show working prototype of device-free authentication

---

## Setup (Before Demo)

1. ✅ Backend running (Railway.app or localhost)
2. ✅ Redis connected
3. ✅ Enrollment app installed on device
4. ✅ Merchant app installed on device
5. ✅ Screen mirroring ready

---

## Demo Flow

### Part 1: Introduction (1 minute)

**Script:**
> "ZeroPay solves a fundamental problem: authentication without devices. No phones, no cards, no tokens. Users authenticate using factors they know - patterns, colors, voices. We use zero-knowledge proofs, so the server never sees the actual authentication data."

### Part 2: Enrollment (3 minutes)

**Actions:**
1. Open enrollment app
2. Show UUID generation
3. Show 13 available factors
4. Select 3 factors:
   - Colors (select 3 colors)
   - Pattern (draw pattern)
   - Emoji (select 4 emojis)
5. Show success screen

**Script:**
> "The enrollment is simple. Users select which factors they want to use. The SDK generates SHA-256 hashes - the server never sees the raw colors or pattern. This is GDPR-compliant by design."

### Part 3: Technical Deep Dive (2 minutes)

**Actions:**
1. Show certificate pinning in code
2. Show encrypted storage
3. Show 24-hour cache expiry

**Script:**
> "Behind the scenes, we have production-grade security: TLS 1.3, certificate pinning, encrypted local storage, and the enrollment data expires after 24 hours. This is PSD3-SCA compliant."

### Part 4: Merchant Authentication (2 minutes)

**Actions:**
1. Open merchant app
2. Enter UUID
3. Authenticate with enrolled factors
4. Show success

**Script:**
> "At the merchant, users just enter their UUID and authenticate. No phone needed. Perfect for:
>- Quick payments at coffee shops
>- Age verification at bars
>- Hotel check-ins
>- Gym access
>- Any scenario where you don't want to carry your phone"

### Part 5: Market & Close (2 minutes)

**Slides:**
- Market size: $2.4T payments
- PSD3 compliance requirement
- Patent-pending zkSNARK approach
- Team & roadmap

---

## Q&A Preparation

**Expected Questions:**

Q: "What if someone forgets their pattern?"  
A: "They can reset at any time. Or use their backup factor - that's why we require minimum 2 factors."

Q: "What about fraud?"  
A: "We have rate limiting, device fingerprinting, and the zkSNARK proofs prevent replay attacks."

Q: "When can you launch?"  
A: "We have a working MVP now. 3 months to beta, 6 months to production with payment gateway integrations."

Q: "What's your moat?"  
A: "Patent-pending zkSNARK implementation, plus network effects - more merchants = more user adoption."

---

## Backup Plan

**If demo breaks:**
1. Have pre-recorded video ready
2. Show code walkthrough instead
3. Walk through architecture diagrams

**Murphy's Law Checklist:**
- [ ] Backup device charged
- [ ] Backend has 2 backup instances
- [ ] Screen recording of working demo
- [ ] PDF of slides on iPad
