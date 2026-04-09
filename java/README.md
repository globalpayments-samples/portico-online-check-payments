# Java — ACH/eCheck Payments

Jakarta EE servlet implementation of ACH/eCheck payment processing using the Global Payments Portico gateway. Processes electronic check payments via direct bank account entry — no card tokenization required.

---

## Requirements

- Java 17+
- Maven 3.8+
- Global Payments Portico credentials (`PUBLIC_API_KEY`, `SECRET_API_KEY`)

---

## Project Structure

```
java/
├── .env.sample
├── pom.xml             # Dependencies (globalpayments-sdk 14.2.20)
├── Dockerfile
├── run.sh
└── src/main/java/com/globalpayments/example/
    ├── ConfigServlet.java          # GET /config
    └── ProcessPaymentServlet.java  # POST /process-payment
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

Build and run:

```bash
mvn clean package
mvn cargo:run
```

Open: http://localhost:8004

---

## Docker

```bash
docker build -t portico-check-java .
docker run -p 8004:8000 --env-file ../.env portico-check-java
```

---

## Implementation

### SDK Configuration

```java
import com.global.api.ServicesContainer;
import com.global.api.serviceConfigs.PorticoConfig;

PorticoConfig config = new PorticoConfig();
config.setSecretApiKey(System.getenv("SECRET_API_KEY"));
config.setServiceUrl("https://cert.api2.heartlandportico.com");
ServicesContainer.configureService(config);
```

### Payment Flow

```
POST /process-payment
  │
  ├─ Validate required fields
  ├─ Validate routing number (ABA checksum)
  ├─ Build ECheck object
  │   ├─ setAccountNumber / setRoutingNumber
  │   ├─ setAccountType  (AccountType.Checking | Savings)
  │   ├─ setCheckType    (CheckType.Personal | Business)
  │   ├─ setEntryMode    (EntryMethod.Manual)
  │   ├─ setCheckName
  │   └─ setSecCode      (SecCode.WEB)
  └─ eCheck.charge(amount).withCurrency("USD").execute()
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

**`java: command not found`**
Install Java 17+: https://adoptium.net

**`mvn: command not found`**
Install Maven 3.8+: https://maven.apache.org/download.cgi

**`Invalid routing number`**
Use `122105155` — a valid ABA routing number that passes checksum validation.

**Build fails on `mvn clean package`**
Run `mvn dependency:resolve` to ensure SDK artifacts are downloaded from Maven Central.

**Port 8004 in use**
Update the port mapping in `docker-compose.yml` or the Cargo plugin configuration in `pom.xml`.
