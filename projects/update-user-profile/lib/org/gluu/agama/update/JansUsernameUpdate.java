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
import org.gluu.agama.smtp.EmailTemplate;
import org.gluu.agama.smtp.jans.model.ContextData;
import io.jans.model.SmtpConfiguration;
import io.jans.service.MailService;

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
    private static final String LANGUAGE = "language";
    private static final SecureRandom RAND = new SecureRandom();

    private static JansUsernameUpdate INSTANCE = null;

    public JansUsernameUpdate() {
    }

    public static synchronized JansUsernameUpdate getInstance() {
        if (INSTANCE == null)
            INSTANCE = new JansUsernameUpdate();

        return INSTANCE;
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
            String lang = getSingleValuedAttr(user, LANGUAGE);

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
            userMap.put(LANGUAGE, lang);


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
        Set<String> attributes = Set.of("uid", "mail");
        User user = getUser(INUM_ATTR, profile.get(INUM_ATTR));

        attributes.forEach(attr -> {
            String val = profile.get(attr);
            LogUtils.log("******** attr: % , val: %", attr, val);
            if (StringHelper.isNotEmpty(val)) {
                user.setAttribute(attr, val);
            }
        });
        user.setUserId(profile.get(UID));
        UserService userService = CdiUtil.bean(UserService.class);
        user = userService.updateUser(user); // Set user status active

        if (user == null) {
            throw new EntryNotFoundException("Added user not found");
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
            String lang = getSingleValuedAttr(user, LANGUAGE);

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
            userMap.put(LANGUAGE, lang);

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

    public boolean sendUsernameUpdateEmail(String to, String newUsername, String language) {
        try {
        // Fetch SMTP configuration
        ConfigurationService configService = CdiUtil.bean(ConfigurationService.class);
        SmtpConfiguration smtpConfig = configService.getConfiguration().getSmtpConfiguration();

        if (smtpConfig == null) {
            LogUtils.log("SMTP configuration is missing.");
            return false;
        }

        // Use preferred language from Agama directly
        String lang = (language != null && !language.isEmpty())
                ? language.toLowerCase()
                : "en"; // fallback to English

        // ✅ Inline translations
        Map<String, Map<String, String>> translations = new HashMap<>();
        translations.put("en", Map.of(
            "subject", "Your username has been updated successfully",
            "body", "Your username has been updated to",
            "footer", "Thanks for keeping your account secure."
        ));
        translations.put("es", Map.of(
            "subject", "Su nombre de usuario se ha actualizado correctamente",
            "body", "Su nombre de usuario se ha actualizado a",
            "footer", "Gracias por mantener su cuenta segura."
        ));
        translations.put("fr", Map.of(
            "subject", "Votre nom d'utilisateur a été mis à jour avec succès",
            "body", "Votre nom d'utilisateur a été mis à jour en",
            "footer", "Merci de garder votre compte sécurisé."
        ));
        translations.put("pt", Map.of(
            "subject", "Seu nome de usuário foi atualizado com sucesso",
            "body", "Seu nome de usuário foi atualizado para",
            "footer", "Obrigado por manter sua conta segura."
        ));
        translations.put("ar", Map.of(
            "subject", "تم تحديث اسم المستخدم الخاص بك بنجاح",
            "body", "تم تحديث اسم المستخدم الخاص بك إلى",
            "footer", "شكرًا للحفاظ على أمان حسابك."
        ));
        translations.put("id", Map.of(
            "subject", "Nama pengguna Anda berhasil diperbarui",
            "body", "Nama pengguna Anda telah diperbarui menjadi",
            "footer", "Terima kasih telah menjaga keamanan akun Anda."
        ));

        // ✅ Pick the right language (fallback to English if missing)
        Map<String, String> bundle = translations.getOrDefault(lang, translations.get("en"));

        // Build context data
        ContextData context = new ContextData();
        context.setDevice("Unknown");
        context.setLocation("Unknown");
        context.setTimeZone("UTC");

        // Prepare localized email content
        String htmlBody = EmailTemplate.get(newUsername, context, bundle);
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
                htmlBody
        );

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
