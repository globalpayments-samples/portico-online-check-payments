package com.globalpayments.example;

import com.global.api.ServicesContainer;
import com.global.api.entities.Address;
import com.global.api.entities.Transaction;
import com.global.api.entities.enums.AccountType;
import com.global.api.entities.enums.CheckType;
import com.global.api.entities.enums.SecCode;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.entities.exceptions.ConfigurationException;
import com.global.api.paymentMethods.eCheck;
import com.global.api.serviceConfigs.PorticoConfig;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;

/**
 * ACH/eCheck Payment Processing Servlet - Direct Entry
 * 
 * This servlet demonstrates ACH/eCheck payment processing using the Global Payments SDK
 * with direct bank account information (account number and routing number) instead of tokenization.
 * This approach is suitable for server-side processing where PCI compliance requirements are met.
 * 
 * Endpoints:
 * - GET /config: Returns minimal configuration for the client
 * - POST /process-payment: Processes ACH/eCheck payments using direct bank account data
 * 
 * @author Global Payments
 * @version 1.0
 */

@WebServlet(urlPatterns = {"/ProcessPaymentServlet", "/config"})
public class ProcessPaymentServlet extends HttpServlet {
    
    private static final long serialVersionUID = 1L;
    private static final Dotenv dotenv = Dotenv.load();
    
    @Override
    public void init() throws ServletException {
        super.init();
        configureSDK();
    }
    
    /**
     * Configure the Global Payments SDK with necessary credentials and settings
     */
    private void configureSDK() {
        try {
            PorticoConfig config = new PorticoConfig();
            config.setSecretApiKey(dotenv.get("SECRET_API_KEY"));
            config.setServiceUrl("https://cert.api2.heartlandportico.com");
            config.setDeveloperId("000000");
            config.setVersionNumber("0000");
            
            ServicesContainer.configureService(config);
        } catch (ConfigurationException e) {
            throw new RuntimeException("Failed to configure SDK", e);
        }
    }
    
    /**
     * Sanitize postal code by removing invalid characters
     * @param postalCode The postal code to sanitize
     * @return The sanitized postal code
     */
    private String sanitizePostalCode(String postalCode) {
        if (postalCode == null || postalCode.trim().isEmpty()) {
            return "";
        }
        return postalCode.replaceAll("[^a-zA-Z0-9-]", "").substring(0, Math.min(postalCode.length(), 10));
    }
    
    /**
     * Validate routing number using the standard checksum algorithm
     * @param routingNumber The 9-digit routing number to validate
     * @return True if the routing number is valid, false otherwise
     */
    private boolean validateRoutingNumber(String routingNumber) {
        if (routingNumber == null || routingNumber.length() != 9 || !routingNumber.matches("\\d{9}")) {
            return false;
        }
        
        int[] digits = routingNumber.chars().map(Character::getNumericValue).toArray();
        int checksum = (
            3 * (digits[0] + digits[3] + digits[6]) +
            7 * (digits[1] + digits[4] + digits[7]) +
            1 * (digits[2] + digits[5] + digits[8])
        ) % 10;
        
        return checksum == 0;
    }
    
    /**
     * Sanitize account number by removing non-numeric characters
     * @param accountNumber The account number to sanitize
     * @return The sanitized account number containing only digits
     */
    private String sanitizeAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return "";
        }
        return accountNumber.replaceAll("[^0-9]", "");
    }
    
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String pathInfo = request.getServletPath();
        
        if ("/config".equals(pathInfo)) {
            // Return minimal configuration for direct entry
            response.setContentType("application/json");
            PrintWriter out = response.getWriter();
            out.println("{");
            out.println("  \"success\": true,");
            out.println("  \"data\": {");
            out.println("    \"directEntry\": true,");
            out.println("    \"message\": \"Direct bank account entry enabled\"");
            out.println("  }");
            out.println("}");
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        try {
            // Validate required fields
            String[] requiredFields = {"account_number", "routing_number", "amount", 
                                     "account_type", "check_type", "check_holder_name"};
            for (String field : requiredFields) {
                String value = request.getParameter(field);
                if (value == null || value.trim().isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    String errorResponse = String.format(
                        "{\"success\":false,\"message\":\"Missing required field: %s\",\"error\":{\"code\":\"VALIDATION_ERROR\",\"details\":\"Missing required field: %s\"}}", 
                        field, field
                    );
                    response.getWriter().write(errorResponse);
                    return;
                }
            }
            
            // Parse and validate amount
            BigDecimal amount;
            try {
                amount = new BigDecimal(request.getParameter("amount"));
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    String errorResponse = String.format(
                        "{\"success\":false,\"message\":\"Amount must be positive\",\"error\":{\"code\":\"VALIDATION_ERROR\",\"details\":\"Amount must be positive\"}}"
                    );
                    response.getWriter().write(errorResponse);
                    return;
                }
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                String errorResponse = String.format(
                    "{\"success\":false,\"message\":\"Invalid amount\",\"error\":{\"code\":\"VALIDATION_ERROR\",\"details\":\"Invalid amount format\"}}"
                );
                response.getWriter().write(errorResponse);
                return;
            }
            
            // Validate and sanitize routing number
            String routingNumber = request.getParameter("routing_number").trim();
            if (!validateRoutingNumber(routingNumber)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                String errorResponse = String.format(
                    "{\"success\":false,\"message\":\"Invalid routing number\",\"error\":{\"code\":\"VALIDATION_ERROR\",\"details\":\"Invalid routing number\"}}"
                );
                response.getWriter().write(errorResponse);
                return;
            }
            
            // Validate and sanitize account number
            String accountNumber = sanitizeAccountNumber(request.getParameter("account_number"));
            if (accountNumber.isEmpty() || accountNumber.length() < 4) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                String errorResponse = String.format(
                    "{\"success\":false,\"message\":\"Invalid account number\",\"error\":{\"code\":\"VALIDATION_ERROR\",\"details\":\"Invalid account number\"}}"
                );
                response.getWriter().write(errorResponse);
                return;
            }
            
            // Parse account and check types
            String accountTypeStr = request.getParameter("account_type").toLowerCase();
            AccountType accountType = "savings".equals(accountTypeStr) ? AccountType.Savings : AccountType.Checking;
            
            String checkTypeStr = request.getParameter("check_type").toLowerCase();
            CheckType checkType = "business".equals(checkTypeStr) ? CheckType.Business : CheckType.Personal;
            
            // Create eCheck data object with direct bank account information
            eCheck check = new eCheck();
            check.setAccountNumber(accountNumber);
            check.setRoutingNumber(routingNumber);
            check.setAccountType(accountType);
            check.setCheckType(checkType);
            check.setSecCode(SecCode.Web);
            check.setCheckName(request.getParameter("check_holder_name"));
            
            // Create address object if zip code is provided
            Address address = null;
            String billingZip = request.getParameter("billing_zip");
            if (billingZip != null && !billingZip.trim().isEmpty()) {
                address = new Address();
                address.setPostalCode(sanitizePostalCode(billingZip));
            }
            
            // Process charge with the specified amount
            Transaction chargeResponse;
            if (address != null) {
                chargeResponse = check.charge(amount)
                    .withAllowDuplicates(true)
                    .withCurrency("USD")
                    .withAddress(address)
                    .execute();
            } else {
                chargeResponse = check.charge(amount)
                    .withAllowDuplicates(true)
                    .withCurrency("USD")
                    .execute();
            }
            
            // Check for successful response code
            if (!"00".equals(chargeResponse.getResponseCode())) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                String errorResponse = String.format(
                    "{\"success\":false,\"message\":\"Payment processing failed\",\"error\":{\"code\":\"PAYMENT_DECLINED\",\"details\":\"%s\"}}", 
                    chargeResponse.getResponseMessage()
                );
                response.getWriter().write(errorResponse);
                return;
            }
            
            // Return success response with transaction ID
            String successResponse = String.format(
                "{\"success\":true,\"message\":\"Payment successful! Transaction ID: %s\",\"data\":{\"transactionId\":\"%s\"}}", 
                chargeResponse.getTransactionId(),
                chargeResponse.getTransactionId()
            );
            response.getWriter().write(successResponse);
            
        } catch (ApiException e) {
            // Handle payment processing errors
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            String errorResponse = String.format(
                "{\"success\":false,\"message\":\"Payment processing failed\",\"error\":{\"code\":\"API_ERROR\",\"details\":\"%s\"}}", 
                e.getMessage()
            );
            response.getWriter().write(errorResponse);
        } catch (Exception e) {
            // Handle unexpected errors
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String errorResponse = String.format(
                "{\"success\":false,\"message\":\"Internal server error\",\"error\":{\"code\":\"INTERNAL_ERROR\",\"details\":\"%s\"}}", 
                e.getMessage()
            );
            response.getWriter().write(errorResponse);
        }
    }
}