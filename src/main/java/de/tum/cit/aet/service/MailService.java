package de.tum.cit.aet.service;

import de.tum.cit.aet.domain.*;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Service for sending emails asynchronously.
 * <p>
 * We use the {@link Async} annotation to send emails asynchronously.
 */
@Service
public class MailService {

    private final Logger log = LoggerFactory.getLogger(MailService.class);

    private static final String USER = "user";

    private static final String BASE_URL = "baseUrl";

    private final JavaMailSender javaMailSender;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    @Value("${benchmarking.mail.from}")
    private String fromAddress;

    @Value("${benchmarking.mail.base-url}")
    private String baseUrl;

    @Autowired
    @Lazy
    private MailService self;

    public MailService(
        JavaMailSender javaMailSender,
        MessageSource messageSource,
        SpringTemplateEngine templateEngine
    ) {
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
    }

    @Async
    public void sendEmail(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        this.sendEmailSync(to, subject, content, isMultipart, isHtml);
    }

    private void sendEmailSync(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        log.debug(
            "Send email[multipart '{}' and html '{}'] to '{}' with subject '{}' and content={}",
            isMultipart,
            isHtml,
            to,
            subject,
            content
        );

        // Prepare message using a Spring helper
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            message.setTo(to);
            message.setFrom(fromAddress);
            message.setSubject(subject);
            message.setText(content, isHtml);
            javaMailSender.send(mimeMessage);
            log.debug("Sent email to User '{}'", to);
        } catch (MailException | MessagingException e) {
            log.warn("Email could not be sent to user '{}'", to, e);
        }
    }

    @Async
    public void sendEmailFromTemplate(User user, String templateName, String titleKey) {
        this.sendEmailFromTemplateSync(user, templateName, titleKey);
    }

    private void sendEmailFromTemplateSync(User user, String templateName, String titleKey) {
        if (user.getEmail() == null) {
            log.debug("Email doesn't exist for user '{}'", user.getLogin());
            return;
        }
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(BASE_URL, baseUrl);
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, locale);
        this.sendEmailSync(user.getEmail(), subject, content, false, true);
    }

    @Async
    public void sendActivationEmail(User user) {
        log.debug("Sending activation email to '{}'", user.getEmail());
        this.sendEmailFromTemplateSync(user, "mail/activationEmail", "email.activation.title");
    }

    @Async
    public void sendCreationEmail(User user) {
        log.debug("Sending creation email to '{}'", user.getEmail());
        this.sendEmailFromTemplateSync(user, "mail/creationEmail", "email.activation.title");
    }

    @Async
    public void sendPasswordResetMail(User user) {
        log.debug("Sending password reset email to '{}'", user.getEmail());
        this.sendEmailFromTemplateSync(user, "mail/passwordResetEmail", "email.reset.title");
    }

    /**
     * Send an email to the user, which is subscribed to a simulation schedule.
     *
     * @param subscriber The subscriber to send the email to.
     */
    @Async
    public void sendSubscribedMail(ScheduleSubscriber subscriber) {
        log.debug("Sending subscription confirmation email to '{}'", subscriber.getEmail());
        Locale locale = Locale.forLanguageTag("en");
        Context context = new Context(locale);
        context.setVariable("subscriber", subscriber);
        context.setVariable(BASE_URL, baseUrl);
        String content = templateEngine.process("mail/scheduleSubscriptionEmail", context);
        String subject = "Artemis-Benchmarking - Subscription to simulation schedule";
        self.sendEmail(subscriber.getEmail(), subject, content, false, true);
    }

    /**
     * Sends the result email for a simulation run to all subscribers of the given schedule.
     * The email is sent asynchronously.
     *
     * @param run      the {@link SimulationRun} containing the details of the completed simulation
     * @param schedule the {@link SimulationSchedule} containing the subscribers to be notified
     * <p>
     * This method creates an email context containing information about the simulation run and
     * its result, processes the email content using a template, and sends the email to all
     * subscribers in the schedule. The emails are sent asynchronously to avoid blocking.
     */
    @Async
    public void sendRunResultMail(SimulationRun run, SimulationSchedule schedule) {
        SimulationResultForSummary result = SimulationResultForSummary.from(run);
        Locale locale = Locale.forLanguageTag("en");
        Context context = new Context(locale);
        context.setVariable("run", run);
        context.setVariable("result", result);
        context.setVariable(BASE_URL, baseUrl);
        String subject = "Artemis-Benchmarking - Result for scheduled run";
        schedule
            .getSubscribers()
            .forEach(subscriber -> {
                context.setVariable("subscriber", subscriber);
                String content = templateEngine.process("mail/subscriptionResultEmail", context);
                log.debug("Sending run result email to '{}'", subscriber.getEmail());
                self.sendEmail(subscriber.getEmail(), subject, content, false, true);
            });
    }

    /**
     * Sends a failure notification email for a simulation run to all subscribers of the given schedule.
     * The email is sent asynchronously.
     *
     * @param run             the {@link SimulationRun} containing details of the failed simulation
     * @param schedule        the {@link SimulationSchedule} containing the subscribers to be notified
     * @param errorLogMessage the {@link LogMessage} containing details about the failure; if null, a default message is used
     * <p>
     * This method creates an email context with details of the simulation run, the error message,
     * and other relevant information. It processes the email content using a template and sends
     * failure notification emails to all subscribers in the schedule. The emails are sent asynchronously
     * to prevent blocking operations.
     */
    @Async
    public void sendRunFailureMail(SimulationRun run, SimulationSchedule schedule, LogMessage errorLogMessage) {
        Locale locale = Locale.forLanguageTag("en");
        Context context = new Context(locale);
        context.setVariable("run", run);
        if (errorLogMessage != null) {
            context.setVariable("error", errorLogMessage.getMessage());
        } else {
            context.setVariable("error", "No error message found.");
        }
        context.setVariable(BASE_URL, baseUrl);
        String subject = "Artemis-Benchmarking - Scheduled run failed";
        schedule
            .getSubscribers()
            .forEach(subscriber -> {
                context.setVariable("subscriber", subscriber);
                String content = templateEngine.process("mail/subscriptionFailureEmail", context);
                log.debug("Sending run failure result email to '{}'", subscriber.getEmail());
                self.sendEmail(subscriber.getEmail(), subject, content, false, true);
            });
    }
}
