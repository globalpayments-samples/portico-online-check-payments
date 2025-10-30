# Global Payments ACH/eCheck Payment Examples

This project demonstrates ACH/eCheck payment processing using the Global Payments SDK across multiple programming languages. Each implementation shows how to process electronic check payments using direct bank account information (account number and routing number) for server-side processing.

## Available Implementations

- [.NET Core](./dotnet/) - ([Preview](https://githubbox.com/globalpayments-samples/portico-online-check-payments/tree/main/dotnet)) - ASP.NET Core web application
- [Java](./java/) - ([Preview](https://githubbox.com/globalpayments-samples/portico-online-check-payments/tree/main/java)) - Jakarta EE servlet-based web application
- [Node.js](./nodejs/) - ([Preview](https://githubbox.com/globalpayments-samples/portico-online-check-payments/tree/main/nodejs)) - Express.js web application
- [PHP](./php/) - ([Preview](https://githubbox.com/globalpayments-samples/portico-online-check-payments/tree/main/php)) - PHP web application

## Project Features

- **Direct Bank Account Entry** - Process payments using account and routing numbers
- **Multiple Account Types** - Support for checking and savings accounts
- **Comprehensive Validation** - Routing number checksum validation and input sanitization
- **Error Handling** - Robust error handling for API and validation errors
- **Multiple Languages** - Consistent implementation across different programming languages

## Implementation Details

Each implementation provides:

1. **ACH/eCheck Processing**
   - Direct bank account information input
   - Account type selection (checking/savings)
   - Check type selection (personal/business)
   - Routing number validation

2. **API Endpoints**
   - GET `/config` - Returns configuration for direct entry processing
   - POST `/process-payment` - Processes ACH/eCheck payments

3. **Security Features**
   - Input validation and sanitization
   - Routing number checksum verification
   - PCI-compliant server-side processing

## Quick Start

1. **Choose your language** - Navigate to any implementation directory (nodejs, php, java, dotnet)
2. **Set up credentials** - Copy `.env.sample` to `.env` and add your Global Payments API keys
3. **Run the server** - Execute `./run.sh` to install dependencies and start the server
4. **Test payments** - Use the web form to process ACH/eCheck payments

## Use Cases

These examples can be adapted for various ACH/eCheck payment scenarios:

- **One-time ACH Payments** - Single electronic check transactions
- **Recurring ACH Payments** - Subscription billing and automatic payments
- **Business-to-Business Payments** - B2B invoice payments and vendor payments
- **Consumer Bill Payments** - Utility bills, loan payments, and online services
- **Refunds and Credits** - Electronic refund processing

## Prerequisites

- Global Payments account with API credentials
- Development environment for your chosen language
- Package manager (npm, composer, maven, dotnet)

## Security Considerations

These examples demonstrate ACH/eCheck payment processing with security best practices:

- **Input Validation** - Comprehensive validation of account numbers, routing numbers, and amounts
- **Routing Number Verification** - Checksum validation using standard banking algorithms
- **Data Sanitization** - Removal of invalid characters from sensitive data
- **Error Handling** - Secure error messages that don't expose sensitive information
- **Server-Side Processing** - All payment data handled server-side for PCI compliance

## Production Deployment

For production use, enhance these examples with:

- **HTTPS Encryption** - Secure data transmission
- **Rate Limiting** - Protection against abuse
- **Logging and Monitoring** - Transaction tracking and error monitoring
- **Compliance Requirements** - NACHA rules and banking regulations
- **Fraud Prevention** - Additional verification and risk management
