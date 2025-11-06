<?php

declare(strict_types=1);

/**
 * ACH/eCheck Payment Processing Script - Direct Entry
 *
 * This script demonstrates ACH/eCheck payment processing using the Global Payments SDK
 * with direct bank account information (account number and routing number) instead of tokenization.
 * This approach is suitable for server-side processing where PCI compliance requirements are met.
 *
 * PHP version 7.4 or higher
 *
 * @category  Payment_Processing
 * @package   GlobalPayments_Sample
 * @author    Global Payments
 * @license   MIT License
 * @link      https://github.com/globalpayments
 */

require_once 'vendor/autoload.php';

use Dotenv\Dotenv;
use GlobalPayments\Api\Entities\Address;
use GlobalPayments\Api\Entities\Enums\AccountType;
use GlobalPayments\Api\Entities\Enums\CheckType;
use GlobalPayments\Api\Entities\Enums\SecCode;
use GlobalPayments\Api\Entities\Exceptions\ApiException;
use GlobalPayments\Api\PaymentMethods\ECheck;
use GlobalPayments\Api\ServiceConfigs\Gateways\PorticoConfig;
use GlobalPayments\Api\ServicesContainer;

ini_set('display_errors', '0');

/**
 * Configure the SDK
 *
 * Sets up the Global Payments SDK with necessary credentials and settings
 * loaded from environment variables.
 *
 * @return void
 */
function configureSdk(): void
{
    $dotenv = Dotenv::createImmutable(__DIR__);
    $dotenv->load();

    $config = new PorticoConfig();
    $config->secretApiKey = $_ENV['SECRET_API_KEY'];
    $config->developerId = '000000';
    $config->versionNumber = '0000';
    $config->serviceUrl = 'https://cert.api2.heartlandportico.com';
    
    ServicesContainer::configureService($config);
}

/**
 * Sanitize postal code by removing invalid characters
 *
 * @param string|null $postalCode The postal code to sanitize
 *
 * @return string Sanitized postal code containing only alphanumeric
 *                characters and hyphens, limited to 10 characters
 */
function sanitizePostalCode(?string $postalCode): string
{
    if ($postalCode === null) {
        return '';
    }
    
    $sanitized = preg_replace('/[^a-zA-Z0-9-]/', '', $postalCode);
    return substr($sanitized, 0, 10);
}

/**
 * Validate routing number using the standard checksum algorithm
 *
 * @param string|null $routingNumber The 9-digit routing number to validate
 * @return bool True if the routing number is valid, false otherwise
 */
function validateRoutingNumber(?string $routingNumber): bool
{
    if (empty($routingNumber) || strlen($routingNumber) !== 9 || !ctype_digit($routingNumber)) {
        return false;
    }
    
    $digits = str_split($routingNumber);
    $checksum = (
        3 * ($digits[0] + $digits[3] + $digits[6]) +
        7 * ($digits[1] + $digits[4] + $digits[7]) +
        1 * ($digits[2] + $digits[5] + $digits[8])
    ) % 10;
    
    return $checksum === 0;
}

/**
 * Sanitize account number by removing non-numeric characters
 *
 * @param string|null $accountNumber The account number to sanitize
 * @return string The sanitized account number containing only digits
 */
function sanitizeAccountNumber(?string $accountNumber): string
{
    if (empty($accountNumber)) {
        return '';
    }
    return preg_replace('/[^0-9]/', '', $accountNumber);
}

// Initialize SDK configuration
configureSdk();

try {
    // Validate required fields for direct ACH/eCheck processing
    if (!isset($_POST['account_number'], $_POST['routing_number'], $_POST['amount'], $_POST['account_type'], $_POST['check_type'], $_POST['check_holder_name'])) {
        throw new ApiException('Missing required fields');
    }
    
    // Parse and validate amount
    $amount = floatval($_POST['amount']);
    if ($amount <= 0) {
        throw new ApiException('Invalid amount');
    }

    // Validate and sanitize routing number
    $routingNumber = trim($_POST['routing_number']);
    if (!validateRoutingNumber($routingNumber)) {
        throw new ApiException('Invalid routing number');
    }
    
    // Validate and sanitize account number
    $accountNumber = sanitizeAccountNumber($_POST['account_number']);
    if (empty($accountNumber) || strlen($accountNumber) < 4) {
        throw new ApiException('Invalid account number');
    }

    switch (strtolower($_POST['account_type'])) {
        case 'savings':
            $accountType = AccountType::SAVINGS;
            break;
        case 'checking':
        default:
            $accountType = AccountType::CHECKING;
            break;
    }

    switch (strtolower($_POST['check_type'])) {
        case 'business':
            $checkType = CheckType::BUSINESS;
            break;
        case 'personal':
        default:
            $checkType = CheckType::PERSONAL;
            break;
    }

    // Initialize eCheck payment data using direct bank account information
    $check = new ECheck();
    $check->accountNumber = $accountNumber;
    $check->routingNumber = $routingNumber;
    $check->accountType = $accountType;
    $check->checkType = $checkType; 
    $check->secCode = SecCode::WEB;
    $check->checkHolderName = $_POST['check_holder_name'];

    // Create billing address if zip code is provided
    $address = null;
    if (!empty($_POST['billing_zip'])) {
        $address = new Address();
        $address->postalCode = sanitizePostalCode($_POST['billing_zip']);
    }

    // Process the ACH/eCheck transaction with specified amount
    $response = $check->charge($amount)
        ->withAllowDuplicates(true)
        ->withCurrency('USD');
    
    if ($address) {
        $response = $response->withAddress($address);
    }
    
    $response = $response->execute();
    
    // Verify transaction was successful
    if ($response->responseCode !== '00') {
        http_response_code(400);
        echo json_encode([
            'success' => false,
            'message' => 'Payment processing failed',
            'error' => [
                'code' => 'PAYMENT_DECLINED',
                'details' => $response->responseMessage
            ]
        ]);
        exit;
    }

    // Return success response with transaction ID
    echo json_encode([
        'success' => true,
        'message' => 'Payment successful! Transaction ID: ' . $response->transactionId,
        'data' => [
            'transactionId' => $response->transactionId
        ]
    ]);
} catch (ApiException $e) {
    // Handle payment processing errors
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'Payment processing failed',
        'error' => [
            'code' => 'API_ERROR',
            'details' => $e->getMessage()
        ]
    ]);
}