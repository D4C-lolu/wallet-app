package com.interswitch.walletapp.services;

import com.interswitch.walletapp.configuration.EmailProperties;
import com.interswitch.walletapp.models.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Slf4j
@Service
public class EmailSenderService {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    public EmailSenderService(EmailProperties emailProperties) {
        this.emailProperties = emailProperties;
        this.mailSender = createMailSender(emailProperties);
    }

    private JavaMailSender createMailSender(EmailProperties props) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(props.host());
        sender.setPort(props.port());
        sender.setUsername(props.username());
        sender.setPassword(props.password());

        Properties mailProps = sender.getJavaMailProperties();
        mailProps.put("mail.transport.protocol", "smtp");
        mailProps.put("mail.smtp.auth", "true");
        mailProps.put("mail.smtp.starttls.enable", "true");

        return sender;
    }

    public boolean send(EmailMessage message) {
        if (!emailProperties.enabled()) {
            log.info("Email sending disabled. Would have sent to: {}", message.to());
            return true;
        }

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(emailProperties.from());
            mail.setTo(message.to());
            mail.setSubject(message.subject());
            mail.setText(message.body());

            mailSender.send(mail);
            log.info("Email sent successfully to: {}", message.to());
            return true;
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", message.to(), e.getMessage());
            return false;
        }
    }
}
