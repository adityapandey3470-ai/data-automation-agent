package com.aditya.dataautomation.auth.service;

import com.aditya.dataautomation.config.properties.MailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
public class EmailService {

    private final MailProperties mailProperties;

    public EmailService(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    public boolean isSmtpConfigured() {
        String host = mailProperties.getHost();
        return host != null && !host.isBlank() && !host.equalsIgnoreCase("localhost.disabled");
    }

    @Async("emailTaskExecutor")
    public boolean sendVerificationEmail(String recipientEmail, String recipientName, String verificationLink) {
        if (!isSmtpConfigured()) {
            log.info("SMTP host not configured (app.mail.host). Skipping physical email delivery for {}", recipientEmail);
            return false;
        }

        try {
            log.info("Dispatching email verification message to {} via SMTP server {}:{}",
                    recipientEmail, mailProperties.getHost(), mailProperties.getPort());

            String subject = "Verify your email address - Tabufinery";
            String bodyHtml = buildVerificationEmailHtml(recipientName, verificationLink);
            sendSmtpMessage(recipientEmail, subject, bodyHtml);
            return true;
        } catch (Exception e) {
            log.error("Failed to transmit email verification to {}: {}", recipientEmail, e.getMessage(), e);
            return false;
        }
    }

    @Async("emailTaskExecutor")
    public boolean sendPasswordResetEmail(String recipientEmail, String resetLink) {
        if (!isSmtpConfigured()) {
            log.info("SMTP host not configured (app.mail.host). Skipping physical email delivery for {}", recipientEmail);
            return false;
        }

        try {
            log.info("Dispatching password reset email to {} via SMTP server {}:{}",
                    recipientEmail, mailProperties.getHost(), mailProperties.getPort());

            String subject = "Reset your password - Tabufinery";
            String bodyHtml = buildPasswordResetEmailHtml(recipientEmail, resetLink);
            sendSmtpMessage(recipientEmail, subject, bodyHtml);
            return true;
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", recipientEmail, e.getMessage(), e);
            return false;
        }
    }

    @Async("emailTaskExecutor")
    public boolean sendAccountAlreadyExistsEmail(String recipientEmail, String recipientName) {
        if (!isSmtpConfigured()) return false;
        try {
            String subject = "Security Notice: Registration attempt on Tabufinery";
            String safeName = HtmlUtils.htmlEscape(recipientName != null ? recipientName : "there");
            String bodyHtml = "<html><body style='font-family:sans-serif;color:#1e293b;'>"
                    + "<p>Hi " + safeName + ",</p>"
                    + "<p>Someone recently attempted to create a new Tabufinery account using your email address (<strong>"
                    + HtmlUtils.htmlEscape(recipientEmail) + "</strong>).</p>"
                    + "<p>If this was you, you can simply log in or reset your password if you forgot it.</p>"
                    + "<p>If you did not make this request, you can safely ignore this email.</p>"
                    + "</body></html>";
            sendSmtpMessage(recipientEmail, subject, bodyHtml);
            return true;
        } catch (Exception e) {
            log.warn("Failed to send account already exists notice to {}: {}", recipientEmail, e.getMessage());
            return false;
        }
    }

    @Async("emailTaskExecutor")
    public boolean sendAccountLockoutAlert(String recipientEmail) {
        if (!isSmtpConfigured()) return false;
        try {
            String subject = "Security Alert: Account temporarily locked - Tabufinery";
            String bodyHtml = "<html><body style='font-family:sans-serif;color:#1e293b;'>"
                    + "<p>Hello,</p>"
                    + "<p>Your Tabufinery account was temporarily locked for 15 minutes due to multiple failed login attempts.</p>"
                    + "<p>If this was not you, we strongly recommend that you reset your password immediately after the lockout expires.</p>"
                    + "</body></html>";
            sendSmtpMessage(recipientEmail, subject, bodyHtml);
            return true;
        } catch (Exception e) {
            log.warn("Failed to send lockout alert to {}: {}", recipientEmail, e.getMessage());
            return false;
        }
    }

    private final com.aditya.dataautomation.common.resilience.SimpleCircuitBreaker smtpBreaker =
            new com.aditya.dataautomation.common.resilience.SimpleCircuitBreaker(5, 60_000);

    /** Up to 3 attempts for network problems (1 s, 3 s pause); after 5 failures in a row SMTP is skipped for 60 s. */
    private void sendSmtpMessage(String to, String subject, String bodyHtml) throws Exception {
        if (!smtpBreaker.allowRequest()) {
            throw new IllegalStateException("SMTP temporarily unavailable (circuit open)");
        }
        try {
            com.aditya.dataautomation.common.resilience.Retry.withBackoff(3, 1000,
                    e -> e instanceof java.io.IOException,
                    () -> {
                        sendSmtpMessageOnce(to, subject, bodyHtml);
                        return null;
                    });
            smtpBreaker.recordSuccess();
        } catch (Exception e) {
            smtpBreaker.recordFailure();
            throw e;
        }
    }

    private void sendSmtpMessageOnce(String to, String subject, String bodyHtml) throws Exception {
        String host = mailProperties.getHost();
        int port = mailProperties.getPort();
        String username = mailProperties.getUsername();
        String password = mailProperties.getPassword();
        String from = mailProperties.getFrom();

        Socket rawSocket = new Socket();
        rawSocket.connect(new java.net.InetSocketAddress(host, port), 10000);
        rawSocket.setSoTimeout(15000);
        Socket activeSocket = rawSocket;

        if (port == 465) {
            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(rawSocket, host, port, true);
            javax.net.ssl.SSLParameters sslParams = sslSocket.getSSLParameters();
            sslParams.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(sslParams);
            sslSocket.startHandshake();
            activeSocket = sslSocket;
        }

        try (Socket socket = activeSocket) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            expectReply(reader, 220);
            sendCommand(writer, "EHLO " + host);
            readEhlo(reader);

            if (port == 587 || port == 25) {
                sendCommand(writer, "STARTTLS");
                String startTlsReply = readReply(reader);
                if (!startTlsReply.startsWith("220")) {
                    throw new RuntimeException("SMTP STARTTLS command failed: " + startTlsReply);
                }

                SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, host, port, true);
                javax.net.ssl.SSLParameters sslParams = sslSocket.getSSLParameters();
                sslParams.setEndpointIdentificationAlgorithm("HTTPS");
                sslSocket.setSSLParameters(sslParams);
                sslSocket.startHandshake();

                reader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream(), StandardCharsets.UTF_8));

                sendCommand(writer, "EHLO " + host);
                readEhlo(reader);
            }

            if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
                sendCommand(writer, "AUTH LOGIN");
                readReply(reader);
                sendCommand(writer, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)));
                readReply(reader);
                sendCommand(writer, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
                String authReply = readReply(reader);
                if (!authReply.startsWith("235")) {
                    throw new RuntimeException("SMTP Authentication failed: " + authReply);
                }
            }

            sendCommand(writer, "MAIL FROM:<" + from + ">");
            expectReply(reader, 250);

            sendCommand(writer, "RCPT TO:<" + to + ">");
            expectReply(reader, 250);

            sendCommand(writer, "DATA");
            expectReply(reader, 354);

            writer.write("From: Tabufinery <" + from + ">\r\n");
            writer.write("To: <" + to + ">\r\n");
            writer.write("Subject: " + subject + "\r\n");
            writer.write("MIME-Version: 1.0\r\n");
            writer.write("Content-Type: text/html; charset=UTF-8\r\n");
            writer.write("\r\n");
            writer.write(bodyHtml);
            writer.write("\r\n.\r\n");
            writer.flush();
            expectReply(reader, 250);

            sendCommand(writer, "QUIT");
        }
    }

    private void sendCommand(BufferedWriter writer, String command) throws Exception {
        writer.write(command + "\r\n");
        writer.flush();
    }

    private String readReply(BufferedReader reader) throws Exception {
        String line = reader.readLine();
        if (line == null) {
            throw new RuntimeException("Unexpected end of stream from SMTP server");
        }
        return line;
    }

    private void expectReply(BufferedReader reader, int expectedCode) throws Exception {
        String line = readReply(reader);
        if (!line.startsWith(String.valueOf(expectedCode))) {
            throw new RuntimeException("Expected SMTP reply " + expectedCode + " but received: " + line);
        }
    }

    private void readEhlo(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break;
            }
        }
    }

    private String buildVerificationEmailHtml(String name, String link) {
        String displayName = (name != null && !name.isBlank()) ? HtmlUtils.htmlEscape(name) : "User";
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>"
                + "<body style='font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;background-color:#0f172a;color:#f8fafc;padding:32px 16px;margin:0;'>"
                + "<div style='max-width:540px;margin:0 auto;background:#1e293b;border:1px solid #334155;border-radius:12px;padding:32px;box-shadow:0 10px 25px rgba(0,0,0,0.3);'>"
                + "<div style='display:flex;align-items:center;gap:10px;margin-bottom:24px;'>"
                + "<div style='width:36px;height:36px;background:#10b981;border-radius:8px;display:inline-flex;align-items:center;justify-content:center;color:#0f172a;font-weight:900;font-size:18px;'>T</div>"
                + "<span style='font-size:20px;font-weight:700;color:#ffffff;letter-spacing:-0.5px;'>Tabufinery</span>"
                + "</div>"
                + "<h2 style='font-size:20px;font-weight:600;margin:0 0 12px 0;color:#ffffff;'>Verify your email address</h2>"
                + "<p style='color:#94a3b8;font-size:14px;line-height:1.6;margin:0 0 20px 0;'>Hello " + displayName + ", thank you for registering with Tabufinery. Please confirm your email address by clicking the button below to activate your account and start analyzing business datasets.</p>"
                + "<div style='text-align:center;margin:28px 0;'>"
                + "<a href='" + link + "' style='background:#10b981;color:#0f172a;font-weight:600;font-size:14px;text-decoration:none;padding:12px 28px;border-radius:8px;display:inline-block;'>Verify Email Address</a>"
                + "</div>"
                + "<p style='color:#64748b;font-size:12px;line-height:1.5;margin:24px 0 0 0;'>This link will expire in 24 hours. If the button above does not work, copy and paste this link into your browser:<br>"
                + "<span style='color:#38bdf8;word-break:break-all;'>" + link + "</span></p>"
                + "<hr style='border:none;border-top:1px solid #334155;margin:24px 0;'>"
                + "<p style='color:#64748b;font-size:11px;margin:0;'>If you did not create a Tabufinery account, please disregard this email.</p>"
                + "</div></body></html>";
    }

    private String buildPasswordResetEmailHtml(String email, String link) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>"
                + "<body style='font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;background-color:#0f172a;color:#f8fafc;padding:32px 16px;margin:0;'>"
                + "<div style='max-width:540px;margin:0 auto;background:#1e293b;border:1px solid #334155;border-radius:12px;padding:32px;box-shadow:0 10px 25px rgba(0,0,0,0.3);'>"
                + "<div style='display:flex;align-items:center;gap:10px;margin-bottom:24px;'>"
                + "<div style='width:36px;height:36px;background:#10b981;border-radius:8px;display:inline-flex;align-items:center;justify-content:center;color:#0f172a;font-weight:900;font-size:18px;'>T</div>"
                + "<span style='font-size:20px;font-weight:700;color:#ffffff;letter-spacing:-0.5px;'>Tabufinery</span>"
                + "</div>"
                + "<h2 style='font-size:20px;font-weight:600;margin:0 0 12px 0;color:#ffffff;'>Reset your password</h2>"
                + "<p style='color:#94a3b8;font-size:14px;line-height:1.6;margin:0 0 20px 0;'>We received a request to reset your password for your Tabufinery account. Click the button below to choose a new password.</p>"
                + "<div style='text-align:center;margin:28px 0;'>"
                + "<a href='" + link + "' style='background:#10b981;color:#0f172a;font-weight:600;font-size:14px;text-decoration:none;padding:12px 28px;border-radius:8px;display:inline-block;'>Reset Password</a>"
                + "</div>"
                + "<p style='color:#64748b;font-size:12px;line-height:1.5;margin:24px 0 0 0;'>This link will expire in 15 minutes. If you did not request a password reset, you can safely ignore this email.</p>"
                + "</div></body></html>";
    }
}
