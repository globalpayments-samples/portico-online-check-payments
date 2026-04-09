# Portico ACH/eCheck Payments

A complete ACH/eCheck payment implementation using the Global Payments Portico gateway. Developers can process electronic check payments using direct bank account information — account number, routing number, and account type — without card tokenization.

Available in four languages: PHP, Node.js, .NET, and Java.

---

## Available Implementations

| Language | Framework | SDK Version | Port |
|----------|-----------|-------------|------|
| [**PHP**](./php/) | Built-in Server | globalpayments/php-sdk ^13.1 | 8003 |
| [**Node.js**](./nodejs/) | Express.js | globalpayments-api ^3.10.6 | 8001 |
| [**.NET**](./dotnet/) | ASP.NET Core | GlobalPayments.Api 9.0.16 | 8006 |
| [**Java**](./java/) | Jakarta Servlet | globalpayments-sdk 14.2.20 | 8004 |

Preview links (runs in browser via CodeSandbox):
- [PHP Preview](https://githubbox.com/globalpayments-samples/portico-online-check-payments/tree/main/php)
- [Node.js Preview](https://githubbox.com/globalpayments-samples/portico-online-check-payments/tree/main/nodejs)
- [.NET Preview](https://githubbox.com/globalpayments-samples/portico-online-check-payments/tree/main/dotnet)
- [Java Preview](https://githubbox.com/globalpayments-samples/portico-online-check-payments/tree/main/java)

---

## How It Works

ACH/eCheck payments are processed entirely server-side. Unlike card payments, there is no client-side tokenization step — the customer enters bank account details directly into the form, and the backend constructs an `ECheck` object and submits it to Portico.

```
Browser
  │
  ├─ GET /config ──────────────────► Server
  │                                    └─ Returns { directEntry: true }
  │                                       (no public key needed)
  │
  ├─ Customer enters bank details
  │   account_number, routing_number,
  │   account_type, check_type, name
  │
  └─ POST /process-payment ────────► Server
      { account_number, routing_number,  └─ Validates routing checksum
        account-type, check-type,        └─ Builds ECheck object
        check_holder_name, amount }      └─ SDK: ECheck.charge().execute()
     ◄── { transactionId, status } ─────┘   via PorticoConfig
```

### Routing Number Validation

All implementations validate routing numbers using the standard ABA checksum algorithm before sending to the gateway. Invalid routing numbers are rejected client-side with a clear error before any API call is made.

---

## Prerequisites

- Global Payments developer account with Portico credentials — [Sign up at developer.globalpay.com](https://developer.globalpay.com)
- Two API keys from your Portico account:
  - `PUBLIC_API_KEY` — prefixed `pkapi_cert_...` (sandbox) or `pkapi_prod_...` (production)
  - `SECRET_API_KEY` — prefixed `skapi_cert_...` (sandbox) or `skapi_prod_...` (production)
- Docker (for multi-service setup), or a local runtime for your chosen language:
  - PHP 8.0+ with Composer
  - Node.js 18+ with npm
  - .NET 8.0 SDK
  - Java 17+ with Maven

---

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/globalpayments-samples/portico-online-check-payments.git
cd portico-online-check-payments
```

### 2. Choose a language and configure credentials

```bash
cd php       # or nodejs, dotnet, java
cp .env.sample .env
```

Edit `.env`:

```env
PUBLIC_API_KEY=pkapi_cert_your_key_here
SECRET_API_KEY=skapi_cert_your_key_here
```

### 3. Install and run

**PHP:**
```bash
composer install
php -S localhost:8003
```
Open: http://localhost:8003

**Node.js:**
```bash
npm install
npm start
```
Open: http://localhost:8001

**.NET:**
```bash
dotnet restore
dotnet run
```
Open: http://localhost:8006

**Java:**
```bash
mvn clean package
mvn cargo:run
```
Open: http://localhost:8004

### 4. Submit a test payment

1. Open the app in your browser
2. Enter a test bank account (see [Test Accounts](#test-accounts) below)
3. Fill in routing number, account type, check type, and amount
4. Click **Submit** — confirm the `transactionId` in the response

---

## Docker Setup

Run all four implementations simultaneously:

```bash
cp .env.sample .env
# Edit .env with your credentials, then:
docker-compose up
```

Individual services:

```bash
docker-compose up nodejs    # http://localhost:8001
docker-compose up php       # http://localhost:8003
docker-compose up java      # http://localhost:8004
docker-compose up dotnet    # http://localhost:8006
```

Run integration tests:

```bash
docker-compose --profile testing up
```

---

## API Endpoints

### `GET /config`

Returns minimal configuration for the payment form. No public key is returned because ACH/eCheck uses direct entry — there is no client-side tokenization.

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

Processes an ACH/eCheck payment using direct bank account data.

**Request body:**
```json
{
  "account_number": "12345678901",
  "routing_number": "122105155",
  "account-type": "checking",
  "check-type": "personal",
  "check_holder_name": "Jane Smith",
  "amount": "25.00",
  "billing_zip": "30303"
}
```

| Field | Required | Values |
|-------|----------|--------|
| `account_number` | Yes | Bank account number (digits only) |
| `routing_number` | Yes | 9-digit ABA routing number |
| `account-type` | Yes | `checking` or `savings` |
| `check-type` | Yes | `personal` or `business` |
| `check_holder_name` | Yes | Name on the bank account |
| `amount` | Yes | Positive decimal (e.g. `25.00`) |
| `billing_zip` | No | Postal code for AVS |

**Success response (`200`):**
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

**Validation error (`400`):**
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

## Test Accounts

Use these in sandbox environments. Portico sandbox does not validate real account numbers — use any plausible format.

| Field | Test Value |
|-------|------------|
| Account Number | `12345678901` |
| Routing Number | `122105155` (valid ABA checksum) |
| Account Type | `checking` |
| Check Type | `personal` |
| Amount | Any positive decimal |

> Sandbox transactions do not move real money. Use sandbox credentials only.

---

## Project Structure

```
portico-online-check-payments/
├── index.html                  # Shared frontend (served by all backends)
├── docker-compose.yml          # Multi-service Docker config
├── Dockerfile.tests            # Playwright test runner
├── LICENSE
├── README.md
│
├── php/                        # Port 8003
│   ├── .env.sample
│   ├── composer.json
│   ├── Dockerfile
│   ├── config.php              # GET /config endpoint
│   ├── process-payment.php     # POST /process-payment endpoint
│   └── index.html
│
├── nodejs/                     # Port 8001
│   ├── .env.sample
│   ├── package.json
│   ├── Dockerfile
│   └── server.js               # Express app: /config, /process-payment
│
├── dotnet/                     # Port 8006
│   ├── .env.sample
│   ├── dotnet.csproj
│   ├── Program.cs
│   ├── Dockerfile
│   └── wwwroot/
│
└── java/                       # Port 8004
    ├── .env.sample
    ├── pom.xml
    ├── Dockerfile
    └── src/
        └── main/java/com/globalpayments/example/
```

---

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `PUBLIC_API_KEY` | Portico public key for reference | `pkapi_cert_jKc1FtuyAydZhZfbB3` |
| `SECRET_API_KEY` | Portico secret key for server-side SDK auth | `skapi_cert_MTyM...` |

> Note: For ACH/eCheck, only `SECRET_API_KEY` is used for transaction processing. `PUBLIC_API_KEY` is present for consistency with other Portico projects.

---

## Troubleshooting

**`Invalid routing number` error**
The routing number failed the ABA checksum validation. Use a valid 9-digit routing number — `122105155` works for sandbox testing.

**`Transaction Declined` response**
Portico sandbox may decline transactions with certain account/routing combinations. Use the test values in [Test Accounts](#test-accounts).

**Port already in use**
Stop the conflicting process (`lsof -i :8001`) or change the port mapping in `docker-compose.yml`.

**PHP — `composer: command not found`**
Install Composer: `curl -sS https://getcomposer.org/installer | php && mv composer.phar /usr/local/bin/composer`

**Java build fails**
Requires Java 17+ and Maven 3.8+. Verify with `java -version` and `mvn -version`.

**.NET — missing packages**
Run `dotnet restore` before `dotnet run`.

---

## License

MIT — see [LICENSE](./LICENSE).
