# PHP — ACH/eCheck Payments

PHP implementation of ACH/eCheck payment processing using the Global Payments Portico gateway. Processes electronic check payments via direct bank account entry — no card tokenization required.

---

## Requirements

- PHP 8.0+
- Composer
- Global Payments Portico credentials (`PUBLIC_API_KEY`, `SECRET_API_KEY`)

---

## Project Structure

```
php/
├── .env.sample         # Environment variable template
├── composer.json       # Dependencies (globalpayments/php-sdk ^13.1)
├── Dockerfile
├── run.sh
├── config.php          # GET /config.php
├── process-payment.php # POST /process-payment.php
└── index.html          # Shared frontend
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
composer install
php -S localhost:8003
```

Open: http://localhost:8003

Or use the convenience script:

```bash
./run.sh
```

---

## Docker

```bash
docker build -t portico-check-php .
docker run -p 8003:8000 --env-file ../.env portico-check-php
```

---

## Implementation

### SDK Configuration

```php
use GlobalPayments\Api\ServiceConfigs\Gateways\PorticoConfig;
use GlobalPayments\Api\ServicesContainer;

$config = new PorticoConfig();
$config->secretApiKey = $_ENV['SECRET_API_KEY'];
$config->serviceUrl   = 'https://cert.api2.heartlandportico.com';
ServicesContainer::configureService($config);
```

### Payment Flow

```
POST /process-payment.php
  │
  ├─ Validate required fields
  ├─ Validate routing number (ABA checksum)
  ├─ Sanitize account number
  ├─ Build ECheck object
  │   ├─ accountNumber
  │   ├─ routingNumber
  │   ├─ accountType (Checking | Savings)
  │   ├─ checkType   (Personal | Business)
  │   ├─ entryMode   (Manual)
  │   ├─ checkName
  │   └─ secCode     (WEB)
  └─ ECheck::charge($amount)->withCurrency('USD')->execute()
```

---

## API Endpoints

### `GET /config.php`

Returns direct-entry flag. No public key is exposed — ACH/eCheck is processed entirely server-side.

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

### `POST /process-payment.php`

Processes an ACH/eCheck charge.

**Request fields:**

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
    "code": "PAYMENT_DECLINED",
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

**`composer: command not found`**
Install Composer: https://getcomposer.org/download/

**`Invalid routing number`**
Use `122105155` — a valid ABA routing number that passes checksum validation.

**Port 8003 in use**
Change the port: `php -S localhost:8004`

**Credentials error**
Confirm `SECRET_API_KEY` starts with `skapi_cert_` for sandbox or `skapi_prod_` for production.
