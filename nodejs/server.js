/**
 * ACH/eCheck Payment Processing Server - Direct Entry
 * 
 * This Express application demonstrates ACH/eCheck payment processing using the Global Payments SDK
 * with direct bank account information (account number and routing number) instead of tokenization.
 * This approach is suitable for server-side processing where PCI compliance requirements are met.
 */

import express from 'express';
import * as dotenv from 'dotenv';
import {
    ServicesContainer,
    PorticoConfig,
    Address,
    ECheck,
    AccountType,
    CheckType,
    ApiError,
    EntryMethod,
    SecCode,
} from 'globalpayments-api';

// Load environment variables from .env file
dotenv.config();

/**
 * Initialize Express application with necessary middleware
 */
const app = express();
const port = process.env.PORT || 8000;

app.use(express.static('.')); // Serve static files
app.use(express.urlencoded({ extended: true })); // Parse form data
app.use(express.json()); // Parse JSON requests

// Configure Global Payments SDK with credentials and settings
const config = new PorticoConfig();
config.secretApiKey = process.env.SECRET_API_KEY;
config.serviceUrl = 'https://cert.api2.heartlandportico.com';
ServicesContainer.configureService(config);

/**
 * Sanitize postal code by removing invalid characters
 * Only allows alphanumeric characters and hyphens, limited to 10 characters
 * 
 * @param {string} postalCode - The postal code to sanitize
 * @returns {string} The sanitized postal code
 */
const sanitizePostalCode = (postalCode) => {
    if (!postalCode) return '';
    return postalCode.replace(/[^a-zA-Z0-9-]/g, '').slice(0, 10);
};

/**
 * Validate routing number using the standard checksum algorithm
 * 
 * @param {string} routingNumber - The 9-digit routing number to validate
 * @returns {boolean} True if the routing number is valid, false otherwise
 */
const validateRoutingNumber = (routingNumber) => {
    if (!routingNumber || routingNumber.length !== 9 || !/^\d{9}$/.test(routingNumber)) {
        return false;
    }
    
    const digits = routingNumber.split('').map(Number);
    const checksum = (
        3 * (digits[0] + digits[3] + digits[6]) +
        7 * (digits[1] + digits[4] + digits[7]) +
        1 * (digits[2] + digits[5] + digits[8])
    ) % 10;
    
    return checksum === 0;
};

/**
 * Sanitize account number by removing non-numeric characters
 * 
 * @param {string} accountNumber - The account number to sanitize
 * @returns {string} The sanitized account number containing only digits
 */
const sanitizeAccountNumber = (accountNumber) => {
    if (!accountNumber) return '';
    return accountNumber.replace(/[^0-9]/g, '');
};

/**
 * GET /config
 * Provide minimal configuration for the client.
 * Since we're using direct entry, no public key is needed.
 */
app.get('/config', (req, res) => {
    res.json({
        success: true,
        data: {
            directEntry: true,
            message: 'Direct bank account entry enabled'
        }
    });
});

/**
 * POST /process-payment
 * Process an ACH/eCheck payment using direct bank account data
 * 
 * Expected form data:
 * - account_number: Bank account number
 * - routing_number: Bank routing number (9 digits)
 * - account-type: Account type (checking or savings)
 * - check-type: Check type (personal or business)
 * - check_holder_name: Name on the account
 * - amount: Payment amount
 * - billing_zip: Billing zip code (optional)
 */
app.post('/process-payment', async (req, res) => {
    try {
        // Validate required fields
        const requiredFields = ['account_number', 'routing_number', 'amount', 
                               'account-type', 'check-type', 'check_holder_name'];
        for (const field of requiredFields) {
            if (!req.body[field]) {
                throw new Error(`Missing required field: ${field}`);
            }
        }

        // Parse and validate amount
        const amount = parseFloat(req.body.amount);
        if (isNaN(amount) || amount <= 0) {
            throw new Error('Invalid amount');
        }

        // Validate and sanitize routing number
        const routingNumber = req.body.routing_number.trim();
        if (!validateRoutingNumber(routingNumber)) {
            throw new Error('Invalid routing number');
        }

        // Validate and sanitize account number
        const accountNumber = sanitizeAccountNumber(req.body.account_number);
        if (!accountNumber || accountNumber.length < 4) {
            throw new Error('Invalid account number');
        }

        // Parse account and check types
        const accountTypeStr = req.body['account-type'].toLowerCase();
        const accountType = accountTypeStr === 'savings' ? AccountType.Savings : AccountType.Checking;
        
        const checkTypeStr = req.body['check-type'].toLowerCase();
        const checkType = checkTypeStr === 'business' ? CheckType.Business : CheckType.Personal;

        // Create eCheck data object with direct bank account information
        const check = new ECheck();
        check.accountNumber = accountNumber;
        check.routingNumber = routingNumber;
        check.accountType = accountType;
        check.checkType = checkType;
        check.entryMode = EntryMethod.Manual;
        check.checkName = req.body.check_holder_name;
        check.secCode = SecCode.WEB;

        // Create address object if zip code is provided
        let address = null;
        if (req.body.billing_zip) {
            address = new Address();
            address.postalCode = sanitizePostalCode(req.body.billing_zip);
        }

        // Process charge with the specified amount
        let chargeBuilder = check.charge(amount)
            .withAllowDuplicates(true)
            .withCurrency('USD');
        
        if (address) {
            chargeBuilder = chargeBuilder.withAddress(address);
        }
            
        const response = await chargeBuilder.execute();

        // Check for successful response code
        if (response.responseCode !== '00') {
            return res.status(400).json({
                success: false,
                message: 'Payment processing failed',
                error: {
                    code: 'PAYMENT_DECLINED',
                    details: response.responseMessage
                }
            });
        }

        // Return success response with transaction ID
        res.json({
            success: true,
            message: `Payment successful! Transaction ID: ${response.transactionId}`,
            data: {
                transactionId: response.transactionId,
                responseCode: response.responseCode,
                responseMessage: response.responseMessage
            }
        });
    } catch (error) {
        // Handle API-specific exceptions and general errors
        console.error('Payment processing error:', error);
        
        const errorCode = error instanceof ApiError ? 'API_ERROR' : 'SERVER_ERROR';
        res.status(400).json({
            success: false,
            message: 'Payment processing failed',
            error: {
                code: errorCode,
                details: error.message
            }
        });
    }
});

// Start the server
app.listen(port, () => {
    console.log(`ACH/eCheck Direct Entry server running at http://localhost:${port}`);
});