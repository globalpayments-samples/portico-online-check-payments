# Portico ACH/eCheck Payments

> Process ACH/eCheck payments via the Portico gateway using direct bank account entry — no card tokenization — demonstrated in PHP, Node.js, .NET, and Java.

## Critical Patterns

1. **`SecCode.WEB` is required on every eCheck object.** All implementations set it (PHP: `$check->secCode = SecCode::WEB`, Node.js: `check.secCode = SecCode.WEB`, .NET: `check.SecCode = "WEB"`, Java: `check.setSecCode(SecCode.Web)`). This tells Portico how the check was authorized. Omitting it causes a Portico rejection with an error message that does not name `secCode` — the failure is hard to trace without knowing this is required.

2. **`withAllowDuplicates(true)` must be set when testing.** Portico blocks repeat charges on the same account/routing combination. Without this flag, sandbox testing reliably hits duplicate-detection errors. All four implementations set it on the charge builder.

3. **No tokenization step — bank data travels directly from form to backend.** There is no `globalpayments.js` iframe or client-side token. Each language has a matched per-language frontend (`php/index.html`, `nodejs/index.html`, `dotnet/wwwroot/index.html`, `java/src/main/webapp/index.html`). Node.js uses hyphenated field names (`account-type`, `check-type`); PHP, .NET, and Java use underscored names (`account_type`, `check_type`). Do not serve one language's frontend with another language's backend.

4. **PHP's `/config` response differs from all other languages.** Node.js, .NET, and Java return `{"data":{"directEntry":true,"message":"..."}}`. PHP's `config.php` returns `{"data":{"publicApiKey":"..."}}` — it exposes the public key rather than the `directEntry` flag. The Node.js/.NET/Java shape is canonical for eCheck; the PHP shape is a copy-paste artifact from a card-payment template.

## Repository Structure

### PHP (Built-in Server)
- [`php/process-payment.php`](php/process-payment.php) — eCheck processing entry point; `configureSdk()`, `validateRoutingNumber()`, `sanitizeAccountNumber()`, `sanitizePostalCode()`
- [`php/config.php`](php/config.php) — serves `GET /config.php`; returns `publicApiKey` (diverges from other languages — see Critical Pattern 4)
- [`php/index.html`](php/index.html) — PHP-specific frontend; form posts directly to `process-payment.php` with underscored field names
- [`php/composer.json`](php/composer.json) — `globalpayments/php-sdk` ^13.1

### Node.js (Express)
- [`nodejs/server.js`](nodejs/server.js) — `validateRoutingNumber`, `sanitizeAccountNumber`, `sanitizePostalCode`; registers `GET /config` and `POST /process-payment`
- [`nodejs/index.html`](nodejs/index.html) — Node.js-specific frontend; uses `account-type` and `check-type` (hyphenated)
- [`nodejs/package.json`](nodejs/package.json) — `globalpayments-api` ^3.10.6

### .NET (ASP.NET Core)
- [`dotnet/Program.cs`](dotnet/Program.cs) — `ConfigureGlobalPaymentsSDK()`, `ConfigureEndpoints()`, `ValidateRoutingNumber()`, `SanitizeAccountNumber()`, `SanitizePostalCode()`
- [`dotnet/wwwroot/index.html`](dotnet/wwwroot/index.html) — .NET-specific frontend
- [`dotnet/dotnet.csproj`](dotnet/dotnet.csproj) — `GlobalPayments.Api` 9.0.16

### Java (Jakarta Servlet)
- [`java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java`](java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java) — `configureSDK()`, `validateRoutingNumber()`, `sanitizeAccountNumber()`, `sanitizePostalCode()`; servlet handles `GET /config` and `POST /ProcessPaymentServlet` (mapped via `@WebServlet(urlPatterns = {"/ProcessPaymentServlet", "/config"})`)
- [`java/src/main/webapp/index.html`](java/src/main/webapp/index.html) — Java-specific frontend
- [`java/pom.xml`](java/pom.xml) — `globalpayments-sdk` 14.2.20

### Shared
- [`docker-compose.yml`](docker-compose.yml) — multi-service config; includes `python` and `go` service entries that reference non-existent directories — those services will fail to build
- [`index.html`](index.html) — root-level generic starter template; NOT the eCheck frontend — each language serves its own per-language copy

## API Surface

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/config` | Returns `directEntry: true` (Node.js/.NET/Java) or `publicApiKey` (PHP) |
| POST | `/process-payment` | PHP / Node.js / .NET: validates ABA checksum; builds `ECheck` object; calls `.charge().execute()` via Portico |
| POST | `/ProcessPaymentServlet` | Java only: same logic, different URL — servlet is mapped via `@WebServlet(urlPatterns = {"/ProcessPaymentServlet", "/config"})` |

PHP does not use a router — `GET /config` is served by `php/config.php` and `POST /process-payment` by `php/process-payment.php` as separate files. Java's POST path is `/ProcessPaymentServlet`, not `/process-payment`.

## Environment Variables

```bash
PUBLIC_API_KEY=pkapi_cert_...   # Portico public key — read by PHP config.php; not used in the ACH charge flow
SECRET_API_KEY=skapi_cert_...   # Portico secret key — required for PorticoConfig SDK auth in all languages
```

`.env.sample` files exist in each language directory. Copy to `.env` and fill in credentials before running.

## Sandbox Test Account

This is an ACH/eCheck sample — no card numbers are involved.

| Field | Test Value |
|-------|------------|
| Account Number | `12345678901` |
| Routing Number | `122105155` (passes ABA checksum algorithm) |
| Account Type | `checking` |
| Check Type | `personal` |
| Amount | Any positive decimal (e.g. `25.00`) |

Portico sandbox does not validate real account data — use any plausible format. Get credentials at [developer.globalpayments.com](https://developer.globalpayments.com).

## Architecture Summary

**Payment flow:** Browser form → ABA checksum validation (client-side JS + server-side) → `POST /process-payment` (PHP/Node.js/.NET) or `POST /ProcessPaymentServlet` (Java) → `ECheck.charge(amount).withAllowDuplicates(true).withCurrency("USD").execute()` → Portico returns `transactionId`

**No client-side tokenization:** Unlike card-payment samples, no `globalpayments.js` is loaded. Bank account data goes directly from form fields to the backend over HTTPS.

## Security Notes

These demos accept raw bank account numbers with no authentication on `/process-payment`. For production: add auth middleware, use tokenization to avoid handling raw account data server-side, and scope PCI DSS compliance accordingly.

## How to Run

```bash
cd php && ./run.sh       # PHP — :8000 (Docker host: :8003)
cd nodejs && ./run.sh    # Node.js — :8000 (Docker host: :8001)
cd dotnet && ./run.sh    # .NET — :8000 (Docker host: :8006)
cd java && ./run.sh      # Java — :8000 (Docker host: :8004)
# All at once:
docker-compose up
```

## How to Verify

```bash
# Config endpoint (Node.js, .NET, Java)
curl http://localhost:8000/config
# Expected: {"success":true,"data":{"directEntry":true,"message":"Direct bank account entry enabled"}}

# Process payment — PHP, .NET (underscored field names)
curl -X POST http://localhost:8000/process-payment \
  -d "account_number=12345678901&routing_number=122105155&account_type=checking&check_type=personal&check_holder_name=Jane+Smith&amount=25.00"
# Expected: {"success":true,"message":"Payment successful! Transaction ID: ...","data":{"transactionId":"..."}}

# Process payment — Node.js (hyphenated field names)
curl -X POST http://localhost:8000/process-payment \
  -d "account_number=12345678901&routing_number=122105155&account-type=checking&check-type=personal&check_holder_name=Jane+Smith&amount=25.00"

# Process payment — Java (different path: /ProcessPaymentServlet)
curl -X POST http://localhost:8000/ProcessPaymentServlet \
  -d "account_number=12345678901&routing_number=122105155&account_type=checking&check_type=personal&check_holder_name=Jane+Smith&amount=25.00"
```

## Making Changes

All language implementations expose identical core behavior, with two structural exceptions: PHP implements endpoints as separate files (`config.php`, `process-payment.php`) rather than router registrations; Java uses `/ProcessPaymentServlet` as the POST path rather than `/process-payment`. A change to the charge logic must be applied to all four languages — each in a separate commit. Do not modify `docker-compose.yml` without noting that the `python` and `go` service blocks reference non-existent directories.

## SDK Versions

- **PHP**: `globalpayments/php-sdk` ^13.1
- **Node.js**: `globalpayments-api` ^3.10.6
- **.NET**: `GlobalPayments.Api` 9.0.16
- **Java**: `globalpayments-sdk` 14.2.20
