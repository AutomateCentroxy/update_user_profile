package org.gluu.agama.smtp;

import java.time.*;
import java.time.format.DateTimeFormatter;
import org.gluu.agama.smtp.jans.model.ContextData;

class EmailTemplate {

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM dd, YYYY, hh:mma (O)");

    static String get(String username, ContextData context) {
        return """
<div style="width: 640px; font-size: 18px; font-family: 'Roboto', sans-serif; font-weight: 300; color: #333;">

    <!-- Header with logo -->
    <div style="background-color: #B29163; padding: 15px; text-align: center;">
        <img src="https://phiwallet.com/components/images/logo.png" alt="PhiWallet Logo" style="height: 50px;">
    </div>

    <!-- Main content -->
    <div style="padding: 20px; border-bottom: 1px solid #ccc;">
        <p><b>Hi,</b><br><br>
        Your username has been successfully updated.</p>

        <div style="display: flex; justify-content: center; margin: 20px 0;">
            <div style="background-color: #F4E2C5; color: #495057; font-size: 30px; font-weight: 500; padding: 10px 20px; border-radius: 8px;" align="center">
                New Username: ${username}
            </div>
        </div>

        <p style="font-size: 14px;">
            If you did not request this change, please contact our support team immediately.
        </p>

        <p>
        Thanks for keeping your account secure.<br>
        The PhiWallet Team
        </p>
    </div>

    <!-- Date Section -->
    <div style="padding: 12px; background-color: #ecf0f5; font-size: 16px;">
        <p style="color: #48596b; font-weight: 500;">When this happened:</p>
        <p><span style="color: #48596b; font-weight: 500;">Date:</span><br>${computeDateTime(context.timeZone)}</p>
    </div>

    <!-- Contact Us Section -->
    <div style="background-color: #f9f9f9; padding: 20px; font-size: 14px; display: flex; justify-content: space-between; align-items: flex-start;">
        <div style="flex: 1;">
            <img src="https://phiwallet.com/components/images/logo.png" alt="Phi Logo" style="height: 40px;">
            <p style="margin: 8px 0;">NIPC: 516547186<br>Gold dealer license: T7164</p>
        </div>
        <div style="flex: 1;">
            <p style="font-weight: bold; margin-bottom: 5px;">Get in touch</p>
            <p>📱 +351 308 802 610<br>
               📱 +34 518 89 80 81<br>
               ✉️ <a href="mailto:support@phiwallet.com" style="color:#333;">support@phiwallet.com</a><br>
               📍 Avenida da Liberdade 262, R/C esquerdo, Lisbon 1250-149, Portugal</p>
        </div>
    </div>

</div>
        """;
    }

    private static String computeDateTime(String zone) {
        Instant now = Instant.now();
        try {
            return now.atZone(ZoneId.of(zone)).format(formatter);
        } catch (Exception e) {
            return now.atOffset(ZoneOffset.UTC).format(formatter);
        }
    }
}
