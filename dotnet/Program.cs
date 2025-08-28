using GlobalPayments.Api;
using GlobalPayments.Api.Entities;
using GlobalPayments.Api.Entities.Enums;
using GlobalPayments.Api.PaymentMethods;
using GlobalPayments.Api.Services;
using dotenv.net;
using System.Text.RegularExpressions;
using System.Text.Json;

namespace ECheckPaymentSample;

/// <summary>
/// ACH/eCheck Payment Processing Application - Direct Entry
/// 
/// This application demonstrates ACH/eCheck payment processing using the Global Payments SDK
/// with direct bank account information (account number and routing number) instead of tokenization.
/// This approach is suitable for server-side processing where PCI compliance requirements are met.
/// </summary>
public class Program
{
    public static void Main(string[] args)
    {
        // Load environment variables from .env file
        DotEnv.Load();

        var builder = WebApplication.CreateBuilder(args);
        
        var app = builder.Build();

        // Configure static file serving for the payment form
        app.UseDefaultFiles();
        app.UseStaticFiles();
        
        // Configure the SDK on startup
        ConfigureGlobalPaymentsSDK();

        ConfigureEndpoints(app);
        
        var port = System.Environment.GetEnvironmentVariable("PORT") ?? "8000";
        app.Urls.Add($"http://0.0.0.0:{port}");
        
        app.Run();
    }

    /// <summary>
    /// Configure the Global Payments SDK with necessary credentials and settings
    /// </summary>
    private static void ConfigureGlobalPaymentsSDK()
    {
        var config = new PorticoConfig
        {
            SecretApiKey = System.Environment.GetEnvironmentVariable("SECRET_API_KEY"),
            ServiceUrl = "https://cert.api2.heartlandportico.com",
            DeveloperId = "000000",
            VersionNumber = "0000"
        };
        
        ServicesContainer.ConfigureService(config);
    }

    /// <summary>
    /// Sanitize postal code by removing invalid characters
    /// </summary>
    /// <param name="postalCode">The postal code to sanitize</param>
    /// <returns>The sanitized postal code</returns>
    private static string SanitizePostalCode(string? postalCode)
    {
        if (string.IsNullOrWhiteSpace(postalCode))
            return string.Empty;

        var sanitized = Regex.Replace(postalCode, @"[^a-zA-Z0-9-]", "");
        return sanitized.Length > 10 ? sanitized.Substring(0, 10) : sanitized;
    }

    /// <summary>
    /// Validate routing number using the standard checksum algorithm
    /// </summary>
    /// <param name="routingNumber">The 9-digit routing number to validate</param>
    /// <returns>True if the routing number is valid, false otherwise</returns>
    private static bool ValidateRoutingNumber(string? routingNumber)
    {
        if (string.IsNullOrWhiteSpace(routingNumber) || 
            routingNumber.Length != 9 || 
            !Regex.IsMatch(routingNumber, @"^\d{9}$"))
        {
            return false;
        }

        var digits = routingNumber.Select(c => int.Parse(c.ToString())).ToArray();
        var checksum = (
            3 * (digits[0] + digits[3] + digits[6]) +
            7 * (digits[1] + digits[4] + digits[7]) +
            1 * (digits[2] + digits[5] + digits[8])
        ) % 10;

        return checksum == 0;
    }

    /// <summary>
    /// Sanitize account number by removing non-numeric characters
    /// </summary>
    /// <param name="accountNumber">The account number to sanitize</param>
    /// <returns>The sanitized account number containing only digits</returns>
    private static string SanitizeAccountNumber(string? accountNumber)
    {
        if (string.IsNullOrWhiteSpace(accountNumber))
            return string.Empty;

        return Regex.Replace(accountNumber, @"[^0-9]", "");
    }


    /// <summary>
    /// Configure the API endpoints for the application
    /// </summary>
    /// <param name="app">The web application</param>
    private static void ConfigureEndpoints(WebApplication app)
    {
        // GET /config - Return minimal configuration for direct entry
        app.MapGet("/config", () =>
        {
            var response = new
            {
                success = true,
                data = new
                {
                    directEntry = true,
                    message = "Direct bank account entry enabled"
                }
            };
            
            return Results.Json(response);
        });

        // POST /process-payment - Process ACH/eCheck payment using direct bank account data
        app.MapPost("/process-payment", async (HttpContext context) =>
        {
            // Parse form data from the request
            var form = await context.Request.ReadFormAsync();
            var accountNumber = form["account_number"].ToString();
            var routingNumber = form["routing_number"].ToString();
            var accountTypeStr = form["account_type"].ToString();
            var checkTypeStr = form["check_type"].ToString();
            var checkHolderName = form["check_holder_name"].ToString();
            var billingZip = form["billing_zip"].ToString();
            var amountStr = form["amount"].ToString();

            // Validate required fields are present
            if (string.IsNullOrEmpty(accountNumber) || string.IsNullOrEmpty(routingNumber) || 
                string.IsNullOrEmpty(accountTypeStr) || string.IsNullOrEmpty(checkTypeStr) || 
                string.IsNullOrEmpty(checkHolderName) || string.IsNullOrEmpty(amountStr))
            {
                return Results.BadRequest(new {
                    success = false,
                    message = "Payment processing failed",
                    error = new {
                        code = "VALIDATION_ERROR",
                        details = "Missing required fields"
                    }
                });
            }

            // Validate and parse amount
            if (!decimal.TryParse(amountStr, out var amount) || amount <= 0)
            {
                return Results.BadRequest(new {
                    success = false,
                    message = "Payment processing failed",
                    error = new {
                        code = "VALIDATION_ERROR",
                        details = "Amount must be a positive number"
                    }
                });
            }

            // Validate and sanitize routing number
            var sanitizedRoutingNumber = routingNumber.Trim();
            if (!ValidateRoutingNumber(sanitizedRoutingNumber))
            {
                return Results.BadRequest(new {
                    success = false,
                    message = "Payment processing failed",
                    error = new {
                        code = "VALIDATION_ERROR",
                        details = "Invalid routing number"
                    }
                });
            }

            // Validate and sanitize account number
            var sanitizedAccountNumber = SanitizeAccountNumber(accountNumber);
            if (string.IsNullOrEmpty(sanitizedAccountNumber) || sanitizedAccountNumber.Length < 4)
            {
                return Results.BadRequest(new {
                    success = false,
                    message = "Payment processing failed",
                    error = new {
                        code = "VALIDATION_ERROR",
                        details = "Invalid account number"
                    }
                });
            }

            // Parse account and check types
            var checkType = string.Equals(accountTypeStr, "business", StringComparison.OrdinalIgnoreCase) 
                ? CheckType.BUSINESS : CheckType.PERSONAL;
            var accountType = string.Equals(checkTypeStr, "savings", StringComparison.OrdinalIgnoreCase) 
                ? AccountType.SAVINGS : AccountType.CHECKING;

            // Initialize eCheck payment data using direct bank account information
            var check = new eCheck
            {
                AccountNumber = sanitizedAccountNumber,
                RoutingNumber = sanitizedRoutingNumber,
                AccountType = accountType,
                CheckType = checkType,
                SecCode = "WEB",
                CheckName = checkHolderName
            };

            // Create billing address if zip code is provided
            Address? address = null;
            if (!string.IsNullOrEmpty(billingZip))
            {
                address = new Address
                {
                    PostalCode = SanitizePostalCode(billingZip)
                };
            }

            try
            {
                // Process the ACH/eCheck transaction using the provided amount
                var chargeBuilder = check.Charge(amount)
                    .WithAllowDuplicates(true)
                    .WithCurrency("USD");
                
                if (address != null)
                {
                    chargeBuilder = chargeBuilder.WithAddress(address);
                }
                
                var response = chargeBuilder.Execute();

                // Verify transaction was successful
                if (response.ResponseCode != "00")
                {
                    return Results.BadRequest(new {
                        success = false,
                        message = "Payment processing failed",
                        error = new {
                            code = "PAYMENT_DECLINED",
                            details = response.ResponseMessage
                        }
                    });
                }

                // Return success response with transaction ID
                return Results.Ok(new
                {
                    success = true,
                    message = $"Payment successful! Transaction ID: {response.TransactionId}",
                    data = new {
                        transactionId = response.TransactionId,
                        responseCode = response.ResponseCode,
                        responseMessage = response.ResponseMessage
                    }
                });
            }
            catch (Exception ex)
            {
                return Results.BadRequest(new {
                    success = false,
                    message = "Payment processing failed",
                    error = new {
                        code = "API_ERROR",
                        details = ex.Message
                    }
                });
            }
        });
    }
}