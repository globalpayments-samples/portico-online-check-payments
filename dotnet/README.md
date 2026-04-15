# .NET — ACH/eCheck Payments

ASP.NET Core implementation of ACH/eCheck payment processing using the Global Payments Portico gateway. Processes electronic check payments via direct bank account entry — no card tokenization required.

---

## Requirements

- .NET 8.0 SDK
- Global Payments Portico credentials (`PUBLIC_API_KEY`, `SECRET_API_KEY`)

---

## Project Structure

```
dotnet/
├── .env.sample         # Environment variable template
├── dotnet.csproj       # Dependencies (GlobalPayments.Api 9.0.16)
├── Program.cs          # ASP.NET Core minimal API: /config, /process-payment
├── appsettings.json
├── Dockerfile
├── run.sh
└── wwwroot/            # Static frontend files
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

Install and run:

```bash
dotnet restore
dotnet run
```

Open: http://localhost:8006

---

## Docker

```bash
docker build -t portico-check-dotnet .
docker run -p 8006:8000 --env-file ../.env portico-check-dotnet
```

---

## Implementation

### SDK Configuration

```csharp
using GlobalPayments.Api;
using GlobalPayments.Api.ServiceConfigs.Gateways;

var config = new PorticoConfig
{
    SecretApiKey = Environment.GetEnvironmentVariable("SECRET_API_KEY"),
    ServiceUrl   = "https://cert.api2.heartlandportico.com"
};
ServicesContainer.ConfigureService(config);
```

### Payment Flow

```
POST /process-payment
  │
  ├─ Validate required fields
  ├─ Validate routing number (ABA checksum)
  ├─ Build ECheck object
  │   ├─ AccountNumber / RoutingNumber
  │   ├─ AccountType  (Checking | Savings)
  │   ├─ CheckType    (Personal | Business)
  │   ├─ EntryMode    (Manual)
  │   ├─ CheckName
  │   └─ SecCode      (WEB)
  └─ eCheck.Charge(amount).WithCurrency("USD").Execute()
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

**`dotnet: command not found`**
Install the .NET 8.0 SDK from https://dotnet.microsoft.com/download

**`Invalid routing number`**
Use `122105155` — a valid ABA routing number. Others will be rejected before the API call.

**Port 8006 in use**
Change `ASPNETCORE_URLS` in `.env` or update the port mapping in `docker-compose.yml`.

**Package restore fails**
Run `dotnet restore` to download NuGet dependencies before `dotnet run`.
