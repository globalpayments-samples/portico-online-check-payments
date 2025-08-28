# Node.js ACH/eCheck Payment Example

This example demonstrates ACH/eCheck payment processing using Express.js and the Global Payments SDK with direct bank account information.

## Requirements

- Node.js 14.x or later
- npm (Node Package Manager)
- Global Payments account and API credentials

## Project Structure

- `server.js` - Main application file containing server setup and ACH/eCheck payment processing
- `index.html` - Client-side bank account information form
- `package.json` - Project dependencies and scripts
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
   npm install
   ```
5. Run the application:
   ```bash
   ./run.sh
   ```
   Or manually:
   ```bash
   node server.js
   ```

## Implementation Details

### Server Setup
The application uses Express.js to create a web server that:
- Serves static files for the bank account form
- Processes ACH/eCheck payment requests
- Provides configuration endpoint for direct entry processing
- Handles JSON and form-encoded requests

### SDK Configuration
Global Payments SDK configuration using environment variables:
- Loads credentials from .env file
- Sets up service URL for API communication
- Configures Portico gateway for ACH processing

### Payment Processing
ACH/eCheck payment processing flow:
1. Client submits bank account details (account number, routing number, account type)
2. Server validates routing number using checksum algorithm
3. Creates eCheck payment method with bank account information
4. Processes ACH charge with specified amount
5. Returns success/error response with transaction details

### Error Handling
Implements comprehensive error handling:
- Routing number validation with checksum verification
- Account number sanitization and validation
- API exception handling with structured error responses
- Input validation for required fields

## API Endpoints

### GET /config
Returns configuration for direct bank account entry processing.

Response:
```json
{
    "success": true,
    "data": {
        "directEntry": true,
        "message": "Direct bank account entry enabled"
    }
}
```

### POST /process-payment
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
```json
{
    "success": true,
    "message": "Payment successful! Transaction ID: xxx",
    "data": {
        "transactionId": "xxx",
        "responseCode": "00",
        "responseMessage": "Success"
    }
}
```

Response (Error):
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

## Security Considerations

This example demonstrates ACH/eCheck processing with security best practices. For production use, consider:
- **HTTPS Encryption** - Secure transmission of bank account data
- **Input Validation** - Enhanced validation beyond routing number checksums
- **Rate Limiting** - Protection against automated attacks
- **Fraud Prevention** - Additional verification and risk assessment
- **Logging and Monitoring** - Transaction tracking and anomaly detection
- **NACHA Compliance** - Adherence to ACH processing regulations
- **Data Retention** - Secure handling and disposal of sensitive bank data
