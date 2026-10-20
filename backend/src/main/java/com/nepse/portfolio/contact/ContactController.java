package com.nepse.portfolio.contact;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);

    public record ContactRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(max = 4000) String message
    ) {
    }

    public record ContactResponse(String message, boolean emailed) {
    }

    private final ContactRepository repository;
    private final JavaMailSender mailSender;
    private final String adminEmail;
    private final String from;

    public ContactController(ContactRepository repository,
                             JavaMailSender mailSender,
                             @Value("${app.contact.admin-email:}") String adminEmail,
                             @Value("${app.contact.from:no-reply@nepse.local}") String from) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.adminEmail = adminEmail;
        this.from = from;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse send(@Valid @RequestBody ContactRequest request) {
        ContactMessage saved = repository.save(
                new ContactMessage(request.name().trim(), request.email().trim(), request.message().trim()));

        boolean emailed = false;
        if (adminEmail != null && !adminEmail.isBlank()) {
            try {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setFrom(from);
                mail.setTo(adminEmail);
                mail.setReplyTo(request.email());
                mail.setSubject("NEPSE Tracker contact: " + request.name());
                mail.setText("From: " + request.name() + " <" + request.email() + ">\n\n" + request.message());
                mailSender.send(mail);
                saved.setEmailSent(true);
                repository.save(saved);
                emailed = true;
            } catch (Exception ex) {
                log.warn("Contact email could not be sent: {}", ex.getMessage());
            }
        }

        return new ContactResponse("Thanks, your message has been recorded.", emailed);
    }
}
