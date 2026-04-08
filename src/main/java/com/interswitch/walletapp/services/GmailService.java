package com.interswitch.walletapp.services;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.interswitch.walletapp.models.dto.EmailMessage;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Properties;

@Slf4j
@Service
public class GmailService {

    @Value("${gmail.api.key:}")
    private String apiKey;

    @Value("${gmail.sender.email:noreply@verveguard.com}")
    private String senderEmail;

    @Value("${gmail.application.name:VerveguardAPI}")
    private String applicationName;

    private Gmail gmail;

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gmail API key not configured. Email service will be disabled.");
            return;
        }
        try {
            gmail = new Gmail.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(GoogleCredentials.create(new AccessToken(apiKey, null)))
            )
                    .setApplicationName(applicationName)
                    .build();
            log.info("Gmail service initialized successfully");
        } catch (GeneralSecurityException | IOException e) {
            log.error("Failed to initialize Gmail service", e);
        }
    }

    public boolean sendEmail(EmailMessage emailMessage) throws MessagingException, IOException {
        if (gmail == null) {
            log.warn("Gmail service not initialized. Skipping email: {}", emailMessage.subject());
            return false;
        }

        MimeMessage mimeMessage = createMimeMessage(emailMessage);
        Message message = createGmailMessage(mimeMessage);

        gmail.users().messages().send("me", message).execute();
        log.info("Email sent successfully to: {}, subject: {}", emailMessage.to(), emailMessage.subject());
        return true;
    }

    private MimeMessage createMimeMessage(EmailMessage emailMessage) throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(new InternetAddress(senderEmail));
        mimeMessage.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(emailMessage.to()));

        if (emailMessage.cc() != null) {
            for (String cc : emailMessage.cc()) {
                mimeMessage.addRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(cc));
            }
        }

        if (emailMessage.bcc() != null) {
            for (String bcc : emailMessage.bcc()) {
                mimeMessage.addRecipient(jakarta.mail.Message.RecipientType.BCC, new InternetAddress(bcc));
            }
        }

        mimeMessage.setSubject(emailMessage.subject());

        if (emailMessage.isHtml()) {
            mimeMessage.setContent(emailMessage.body(), "text/html; charset=utf-8");
        } else {
            mimeMessage.setText(emailMessage.body());
        }

        return mimeMessage;
    }

    private Message createGmailMessage(MimeMessage mimeMessage) throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.getUrlEncoder().encodeToString(bytes);

        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    public boolean isConfigured() {
        return gmail != null;
    }
}
