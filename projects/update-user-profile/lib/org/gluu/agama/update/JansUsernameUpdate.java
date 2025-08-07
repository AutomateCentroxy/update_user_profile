package org.gluu.agama.update;

import io.jans.as.common.model.common.User;
import io.jans.as.common.service.common.EncryptionService;
import io.jans.as.common.service.common.UserService;
import io.jans.orm.exception.operation.EntryNotFoundException;
import io.jans.service.cdi.util.CdiUtil;
import io.jans.util.StringHelper;

import org.gluu.agama.user.UsernameUpdate;
import io.jans.agama.engine.script.LogUtils;
import java.io.IOException;
import io.jans.as.common.service.common.ConfigurationService;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.regex.Pattern;
import org.gluu.agama.smtp.SendEmailTemplate;
import org.gluu.agama.smtp.jans.model.ContextData;
import io.jans.model.SmtpConfiguration;
import io.jans.service.MailService;
import io.jans.as.model.common.IntrospectionResponse;
import io.jans.as.server.service.IntrospectionService;
import io.jans.agama.engine.service.ActionService;
import jakarta.servlet.http.HttpServletRequest;

public class JansUsernameUpdate extends UsernameUpdate {

    private static final String MAIL = "mail";
    private static final String UID = "uid";
    private static final String DISPLAY_NAME = "displayName";
    private static final String GIVEN_NAME = "givenName";
    private static final String LAST_NAME = "sn";
    private static final String PASSWORD = "userPassword";
    private static final String INUM_ATTR = "inum";
    private static final String EXT_ATTR = "jansExtUid";
    private static final String USER_STATUS = "jansStatus";
    private static final String EXT_UID_PREFIX = "github:";
    private static final String LANG = "lang";
    private static final SecureRandom RAND = new SecureRandom();

    private static JansUsernameUpdate INSTANCE = null;

    public JansUsernameUpdate() {
    }

    public static synchronized JansUsernameUpdate getInstance() {
        if (INSTANCE == null)
            INSTANCE = new JansUsernameUpdate();

        return INSTANCE;
    }


    public static Map<String, Object> validateBearerToken() {
    Map<String, Object> result = new HashMap<>();

    try {
        ActionService actionService = CdiUtil.bean(ActionService.class);
        Map<String, String> headers = actionService.getRequestHeaders();
        String authHeader = headers.get("Authorization");

        if (authHeader == null || authHeader.isEmpty()) {
            LogUtils.log("ERROR: No Authorization header provided");
            result.put("valid", false);
            result.put("error", "Missing Authorization header. Bearer token required.");
            return result;
        }

        if (!authHeader.startsWith("Bearer ")) {
            LogUtils.log("ERROR: Authorization header does not contain Bearer token");
            result.put("valid", false);
            result.put("error", "Invalid Authorization header format. Expected: Bearer <token>");
            return result;
        }

        String token = authHeader.substring(7).trim();

        if (token.isEmpty()) {
            LogUtils.log("ERROR: Bearer token is empty");
            result.put("valid", false);
            result.put("error", "Bearer token is empty");
            return result;
        }

        IntrospectionService introspectionService = CdiUtil.bean(IntrospectionService.class);
        IntrospectionResponse introspectionResponse = introspectionService.introspect(token);

        if (introspectionResponse == null || !introspectionResponse.isActive()) {
            LogUtils.log("ERROR: Token is not active or introspection failed");
            result.put("valid", false);
            result.put("error", "Token is invalid or expired");
            return result;
        }

        String scopes = introspectionResponse.getScope();
        boolean hasRequiredScope = scopes != null && (
            scopes.contains("profile") ||
            scopes.contains("user_update") ||
            scopes.contains("openid")
        );

        if (!hasRequiredScope) {
            LogUtils.log("ERROR: Token does not have required scope. Has: " + scopes);
            result.put("valid", false);
            result.put("error", "Token does not have required scope (profile, user_update, or openid)");
            return result;
        }

        result.put("valid", true);
        result.put("clientId", introspectionResponse.getClientId());
        result.put("username", introspectionResponse.getUsername());
        result.put("scopes", scopes);

        LogUtils.log("Bearer token validated successfully. Client: " + introspectionResponse.getClientId());

    } catch (Exception e) {
        LogUtils.log("ERROR: Bearer token validation failed: " + e.getMessage());
        result.put("valid", false);
        result.put("error", "Token validation error: " + e.getMessage());
    }

    return result;
    }

    public boolean passwordPolicyMatch(String userPassword) {
        String regex = '''^(?=.*[!@#$^&*])[A-Za-z0-9!@#$^&*]{6,}$'''
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(userPassword).matches();
    }

    public boolean usernamePolicyMatch(String userName) {
        // Regex: Only alphabets (uppercase and lowercase), minimum 1 character
        String regex = '''^[A-Za-z]+$''';
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(userName).matches();
    }

    public Map<String, String> getUserEntityByMail(String email) {
        User user = getUser(MAIL, email);
        boolean local = user != null;
        LogUtils.log("There is % local account for %", local ? "a" : "no", email);

        if (local) {
            String uid = getSingleValuedAttr(user, UID);
            String inum = getSingleValuedAttr(user, INUM_ATTR);
            String name = getSingleValuedAttr(user, GIVEN_NAME);

            if (name == null) {
                name = getSingleValuedAttr(user, DISPLAY_NAME);
                if (name == null && email != null && email.contains("@")) {
                    name = email.substring(0, email.indexOf("@"));
                }
            }

            // Creating a truly modifiable map
            Map<String, String> userMap = new HashMap<>();
            userMap.put(UID, uid);
            userMap.put(INUM_ATTR, inum);
            userMap.put("name", name);
            userMap.put("email", email);

            return userMap;
        }

        return new HashMap<>();
    }

    public Map<String, String> getUserEntityByUsername(String username) {
        User user = getUser(UID, username);
        boolean local = user != null;
        LogUtils.log("There is % local account for %", local ? "a" : "no", username);

        if (local) {
            String email = getSingleValuedAttr(user, MAIL);
            String inum = getSingleValuedAttr(user, INUM_ATTR);
            String name = getSingleValuedAttr(user, GIVEN_NAME);
            String uid = getSingleValuedAttr(user, UID); // Define uid properly
            String displayName = getSingleValuedAttr(user, DISPLAY_NAME);
            String givenName = getSingleValuedAttr(user, GIVEN_NAME);
            String sn = getSingleValuedAttr(user, LAST_NAME);
            String lang = getSingleValuedAttr(user, LANG);

            if (name == null) {
                name = getSingleValuedAttr(user, DISPLAY_NAME);
                if (name == null && email != null && email.contains("@")) {
                    name = email.substring(0, email.indexOf("@"));
                }
            }
            // Creating a modifiable HashMap directly
            Map<String, String> userMap = new HashMap<>();
            userMap.put(UID, uid);
            userMap.put(INUM_ATTR, inum);
            userMap.put("name", name);
            userMap.put("email", email);
            userMap.put(DISPLAY_NAME, displayName);
            userMap.put(LAST_NAME, sn);
            userMap.put(LANG, lang);

            return userMap;
        }

        return new HashMap<>();
    }

    public String addNewUser(Map<String, String> profile) throws Exception {
        Set<String> attributes = Set.of("uid", "mail", "displayName", "givenName", "sn", "userPassword");
        User user = new User();

        attributes.forEach(attr -> {
            String val = profile.get(attr);
            if (StringHelper.isNotEmpty(val)) {
                user.setAttribute(attr, val);
            }
        });

        UserService userService = CdiUtil.bean(UserService.class);
        user = userService.addUser(user, true); // Set user status active

        if (user == null) {
            throw new EntryNotFoundException("Added user not found");
        }

        return getSingleValuedAttr(user, INUM_ATTR);
    }

    public String updateUser(Map<String, String> profile) throws Exception {
        String inum = profile.get(INUM_ATTR);
        User user = getUser(INUM_ATTR, inum);

        if (user == null) {
            throw new EntryNotFoundException("User not found for inum: " + inum);
        }

        // 🔒 Preserve current email and lang
        String currentEmail = getSingleValuedAttr(user, MAIL);
        String currentLanguage = getSingleValuedAttr(user, LANG);

        // ✅ Update UID if provided
        String newUid = profile.get(UID);
        if (StringHelper.isNotEmpty(newUid)) {
            user.setAttribute(UID, newUid);
            user.setUserId(newUid);
        }

        // ✅ Always preserve email and lang
        if (StringHelper.isNotEmpty(currentEmail)) {
            user.setAttribute(MAIL, currentEmail);
        }
        if (StringHelper.isNotEmpty(currentLanguage)) {
            user.setAttribute(LANG, currentLanguage);
        }

        // ✅ Save the user
        UserService userService = CdiUtil.bean(UserService.class);
        user = userService.updateUser(user);

        if (user == null) {
            throw new EntryNotFoundException("Updated user not found");
        }

        return getSingleValuedAttr(user, INUM_ATTR);
    }

    public Map<String, String> getUserEntityByInum(String inum) {
        User user = getUser(INUM_ATTR, inum);
        boolean local = user != null;
        LogUtils.log("There is % local account for %", local ? "a" : "no", inum);

        if (local) {
            String email = getSingleValuedAttr(user, MAIL);
            // String inum = getSingleValuedAttr(user, INUM_ATTR);
            String name = getSingleValuedAttr(user, GIVEN_NAME);
            String uid = getSingleValuedAttr(user, UID); // Define uid properly
            String displayName = getSingleValuedAttr(user, DISPLAY_NAME);
            String givenName = getSingleValuedAttr(user, GIVEN_NAME);
            String sn = getSingleValuedAttr(user, LAST_NAME);
            String userPassword = getSingleValuedAttr(user, PASSWORD);
            String lang = getSingleValuedAttr(user, LANG);

            if (name == null) {
                name = getSingleValuedAttr(user, DISPLAY_NAME);
                if (name == null && email != null && email.contains("@")) {
                    name = email.substring(0, email.indexOf("@"));
                }
            }
            // Creating a modifiable HashMap directly
            Map<String, String> userMap = new HashMap<>();
            userMap.put(UID, uid);
            userMap.put("userId", uid);
            userMap.put(INUM_ATTR, inum);
            userMap.put("name", name);
            userMap.put("email", email);
            userMap.put(DISPLAY_NAME, displayName);
            userMap.put(LAST_NAME, sn);
            userMap.put(PASSWORD, userPassword);
            userMap.put(LANG, lang);

            return userMap;
        }

        return new HashMap<>();
    }

    private String getSingleValuedAttr(User user, String attribute) {
        Object value = null;
        if (attribute.equals(UID)) {
            // user.getAttribute("uid", true, false) always returns null :(
            value = user.getUserId();
        } else {
            value = user.getAttribute(attribute, true, false);
        }
        return value == null ? null : value.toString();

    }

    private User getUser(String attributeName, String value) {
        UserService userService = CdiUtil.bean(UserService.class);
        return userService.getUserByAttribute(attributeName, value, true);
    }

    public boolean sendUsernameUpdateEmail(String to, String newUsername, String lang) {
        try {
            // Fetch SMTP configuration
            ConfigurationService configService = CdiUtil.bean(ConfigurationService.class);
            SmtpConfiguration smtpConfig = configService.getConfiguration().getSmtpConfiguration();

            if (smtpConfig == null) {
                LogUtils.log("SMTP configuration is missing.");
                return false;
            }

            // Use preferred lang from Agama directly
            String preferredLang = (lang != null && !lang.isEmpty())
                    ? lang.toLowerCase()
                    : "en"; // fallback to English

            // ✅ Inline translations
            Map<String, Map<String, String>> translations = new HashMap<>();
            translations.put("en", Map.of(
                    "subject", "Your username has been updated successfully",
                    "body", "Your username has been updated to",
                    "footer", "Thanks for keeping your account secure."));
            translations.put("es", Map.of(
                    "subject", "Su nombre de usuario se ha actualizado correctamente",
                    "body", "Su nombre de usuario se ha actualizado a",
                    "footer", "Gracias por mantener su cuenta segura."));
            translations.put("fr", Map.of(
                    "subject", "Votre nom d'utilisateur a été mis à jour avec succès",
                    "body", "Votre nom d'utilisateur a été mis à jour en",
                    "footer", "Merci de garder votre compte sécurisé."));
            translations.put("pt", Map.of(
                    "subject", "Seu nome de usuário foi atualizado com sucesso",
                    "body", "Seu nome de usuário foi atualizado para",
                    "footer", "Obrigado por manter sua conta segura."));
            translations.put("ar", Map.of(
                    "subject", "تم تحديث اسم المستخدم الخاص بك بنجاح",
                    "body", "تم تحديث اسم المستخدم الخاص بك إلى",
                    "footer", "شكرًا للحفاظ على أمان حسابك."));
            translations.put("id", Map.of(
                    "subject", "Nama pengguna Anda berhasil diperbarui",
                    "body", "Nama pengguna Anda telah diperbarui menjadi",
                    "footer", "Terima kasih telah menjaga keamanan akun Anda."));

            // ✅ Pick the right lang (fallback to English if missing)
            Map<String, String> bundle = translations.getOrDefault(preferredLang, translations.get("en"));

            // Build context data
            ContextData context = new ContextData();
            context.setDevice("Unknown");
            context.setLocation("Unknown");
            context.setTimeZone("UTC");

            // Prepare localized email content
            String htmlBody = SendEmailTemplate.get(newUsername, context, bundle);
            String subject = bundle.get("subject");
            String textBody = bundle.get("body") + ": " + newUsername;

            // Send signed email
            MailService mailService = CdiUtil.bean(MailService.class);
            boolean sent = mailService.sendMailSigned(
                    smtpConfig.getFromEmailAddress(),
                    smtpConfig.getFromName(),
                    to,
                    null,
                    subject,
                    textBody,
                    htmlBody);

            LogUtils.log("Localized username update email sent successfully to %", to);
            return sent;
        } catch (Exception e) {
            LogUtils.log("Failed to send username update email: %", e.getMessage());
            return false;
        }
    }

    // Helper method to fetch SMTP configuration
    private SmtpConfiguration getSmtpConfiguration() {
        ConfigurationService configurationService = CdiUtil.bean(ConfigurationService.class);
        return configurationService.getConfiguration().getSmtpConfiguration();
    }
}