# Node.js — ACH/eCheck Payments

Node.js/Express implementation of ACH/eCheck payment processing using the Global Payments Portico gateway. Processes electronic check payments via direct bank account entry with server-side routing number validation.

---

## Requirements

- Node.js 18+
- npm
- Global Payments Portico credentials (`PUBLIC_API_KEY`, `SECRET_API_KEY`)

---

## Project Structure

```
nodejs/
├── .env.sample     # Environment variable template
├── package.json    # Dependencies (globalpayments-api ^3.10.6)
├── Dockerfile
├── run.sh
├── server.js       # Express app: /config, /process-payment
└── index.html      # Shared frontend
```

---

## Setup

```bash
cp .env.sample .env
```

Edit `.env`:

```env
PUBLIC_API_KEY=pkapi_cert_your_key_here
SECRET_API_KEY=skapi_cert_your_key_here
```

Install dependencies and start:

```bash
npm install
npm start
```

Open: http://localhost:8001

---

## Docker

```bash
docker build -t portico-check-nodejs .
docker run -p 8001:8000 --env-file ../.env portico-check-nodejs
```

---

## Implementation

### SDK Configuration

```js
import { ServicesContainer, PorticoConfig } from 'globalpayments-api';

const config = new PorticoConfig();
config.secretApiKey = process.env.SECRET_API_KEY;
config.serviceUrl   = 'https://cert.api2.heartlandportico.com';
ServicesContainer.configureService(config);
```

### Payment Flow

```
POST /process-payment
  │
  ├─ Validate required fields
  ├─ validateRoutingNumber() — ABA checksum algorithm
  ├─ sanitizeAccountNumber() — strip non-digits
  ├─ Build ECheck object
  │   ├─ accountNumber / routingNumber
  │   ├─ accountType  (AccountType.Checking | Savings)
  │   ├─ checkType    (CheckType.Personal | Business)
  │   ├─ entryMode    (EntryMethod.Manual)
  │   ├─ checkName
  │   └─ secCode      (SecCode.WEB)
  └─ check.charge(amount).withCurrency('USD').execute()
```

### Routing Number Validation

```js
const checksum = (
  3 * (d[0] + d[3] + d[6]) +
  7 * (d[1] + d[4] + d[7]) +
  1 * (d[2] + d[5] + d[8])
) % 10;
// Valid if checksum === 0
```

---

## API Endpoints

### `GET /config`

Returns direct-entry flag. No public key exposed — ACH/eCheck is server-side only.

**Response:**
```json
{
  "success": true,
  "data": {
    "directEntry": true,
    "message": "Direct bank account entry enabled"
  }
}
```

---

### `POST /process-payment`

Processes an ACH/eCheck charge.

**Request body:**

| Field | Required | Description |
|-------|----------|-------------|
| `account_number` | Yes | Bank account number |
| `routing_number` | Yes | 9-digit ABA routing number |
| `account-type` | Yes | `checking` or `savings` |
| `check-type` | Yes | `personal` or `business` |
| `check_holder_name` | Yes | Name on the account |
| `amount` | Yes | Positive decimal |
| `billing_zip` | No | Postal code for AVS |

**Success (`200`):**
```json
{
  "success": true,
  "message": "Payment successful! Transaction ID: 1234567890",
  "data": {
    "transactionId": "1234567890",
    "responseCode": "00",
    "responseMessage": "Transaction Approved"
  }
}
```

**Error (`400`):**
```json
{
  "success": false,
  "message": "Payment processing failed",
  "error": {
    "code": "VALIDATION_ERROR",
    "details": "Invalid routing number"
  }
}
```

---

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `PUBLIC_API_KEY` | Portico public key | `pkapi_cert_jKc1Ft...` |
| `SECRET_API_KEY` | Portico secret key (used for SDK auth) | `skapi_cert_MTyM...` |
| `PORT` | Server port (default: `8000`) | `8000` |

---

## Test Values

| Field | Value |
|-------|-------|
| Account Number | `12345678901` |
| Routing Number | `122105155` |
| Account Type | `checking` |
| Check Type | `personal` |
| Amount | Any positive decimal |

---

## Troubleshooting

**`Invalid routing number`**
Use `122105155` — passes ABA checksum. Other numbers will be rejected before the API call.

**Port 8001 in use**
Set `PORT=8002` in `.env` or change the host mapping in `docker-compose.yml`.

**`MODULE_NOT_FOUND` error**
Run `npm install` before starting.

**Credentials error from Portico**
Confirm `SECRET_API_KEY` starts with `skapi_cert_` for sandbox.
