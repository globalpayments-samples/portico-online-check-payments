# PHP ACH/eCheck Payment Example

This example demonstrates ACH/eCheck payment processing using PHP and the Global Payments SDK with direct bank account information.

## Requirements

- PHP 7.4 or later
- Composer
- Global Payments account and API credentials

## Project Structure

- `process-payment.php` - Payment processing script
- `index.html` - Client-side payment form
- `composer.json` - Project dependencies
- `.env.sample` - Template for environment variables
- `run.sh` - Convenience script to run the application

## Setup

1. Clone this repository
2. Copy `.env.sample` to `.env`
3. Update `.env` with your Global Payments credentials:
   ```
   PUBLIC_API_KEY=pk_test_xxx
   SECRET_API_KEY=sk_test_xxx
   ```
4. Install dependencies:
   ```bash
   composer install
   ```
5. Run the application:
   ```bash
   ./run.sh
   ```
   Or manually:
   ```bash
   php -S localhost:8000
   ```

## Implementation Details

### Application Structure
The application uses a simple PHP structure:
- Static HTML form for payment collection
- Separate PHP script for payment processing
- Composer for dependency management

### SDK Configuration
Global Payments SDK configuration using environment variables:
- Loads credentials from .env file
- Sets up service URL for API communication
- Configures developer identification

### Payment Processing
Payment processing flow:
1. Client submits payment token and billing zip
2. Server creates CreditCardData with token
3. Creates Address with postal code
4. Processes $10 USD charge
5. Returns success/error response

### Error Handling
Implements comprehensive error handling:
- Catches and processes API exceptions
- Returns appropriate error messages
- Handles edge cases gracefully

## API Endpoints

### POST /process-payment.php
Processes an ACH/eCheck payment using direct bank account information.

Request Parameters:
- `account_number` (string, required) - Bank account number
- `routing_number` (string, required) - Bank routing number (9 digits)
- `account_type` (string, required) - Account type ("checking" or "savings")
- `check_type` (string, required) - Check type ("personal" or "business")
- `check_holder_name` (string, required) - Name on the account
- `amount` (number, required) - Payment amount
- `billing_zip` (string, optional) - Billing zip code

Response (Success):
```
Payment successful! Transaction ID: xxx
```

Response (Error):
```
Error: [error message]
```

## Security Considerations

This example demonstrates ACH/eCheck processing with security best practices. For production use, consider:
- **HTTPS Encryption** - Secure transmission of bank account data
- **Enhanced Validation** - Additional routing number and account validation
- **Rate Limiting** - Protection against automated attacks
- **CSRF Protection** - Cross-site request forgery prevention
- **Session Security** - Proper session handling and timeout
- **NACHA Compliance** - Adherence to ACH processing regulations
- **Error Logging** - Secure logging without exposing sensitive data
