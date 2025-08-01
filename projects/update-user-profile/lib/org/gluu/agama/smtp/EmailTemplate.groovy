package org.gluu.agama.smtp;

import java.time.*;
import java.time.format.DateTimeFormatter;
import org.gluu.agama.smtp.jans.model.ContextData;

class EmailTemplate {

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM dd, YYYY, HH:mma (O)");

    static String get(String username, ContextData context) {
        return """
<div style="width: 640px; font-size: 18px; font-family: 'Roboto', sans-serif; font-weight: 300">
    <div style="padding: 12px; border-bottom: 1px solid #ccc;">
        <p>
        <b>Hi,</b>
        <br><br>
        Your username has been successfully updated.
        </p>
        <div style="display: flex; justify-content: center; margin: 20px 0;">
            <div style="background-color: #b6f6da; color: #0ca65d; font-size: 30px; font-weight: 500; padding: 10px 20px; border-radius: 8px;" align="center">
                New Username: ${username}
            </div>
        </div>
        <p style="font-size: 14px">
        If you did not request this change, please contact our support team immediately.
        </p>
        <p>
        <br>
        Thanks for keeping your account secure.<br>
        The PhiWallet Team
        <br><br>
        </p>
    </div>
    <div style="padding: 12px; background-color: #ecf0f5; font-size: 16px">
        <p style="color: #48596b; font-weight: 500">When and where this happened:</p>
        <p><span style="color: #48596b; font-weight: 500">Date:</span><br>${computeDateTime(context.timeZone)}</p>
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

