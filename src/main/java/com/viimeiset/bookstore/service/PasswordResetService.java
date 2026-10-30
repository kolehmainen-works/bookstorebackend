package com.viimeiset.bookstore.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.viimeiset.bookstore.domain.AppUser;
import com.viimeiset.bookstore.domain.AppUserRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class PasswordResetService {

	private static final long TOKEN_VALID_MINUTES = 60;

	private final AppUserRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final JavaMailSender mailSender;
	private final String mailFrom;
	private final String frontendUrl;

	public PasswordResetService(AppUserRepository repository, PasswordEncoder passwordEncoder,
			JavaMailSender mailSender, @Value("${spring.mail.username}") String mailFrom,
			@Value("${app.frontend-url}") String frontendUrl) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.mailSender = mailSender;
		// the sender has to be the same as the SMTP login (spring.mail.username)
		this.mailFrom = mailFrom;
		this.frontendUrl = frontendUrl.replaceAll("/+$", "");
	}

	/**
	 * Sends a reset link to the user with this email. Does nothing if there is
	 * no such user, so the caller cannot find out which emails are registered.
	 */
	public void sendResetLink(String email) throws MessagingException {
		AppUser appUser = repository.findByEmail(email);
		if (appUser == null) {
			return;
		}

		String token = UUID.randomUUID().toString().replaceAll("-", "");
		appUser.setResetToken(token);
		appUser.setResetTokenExpiresAt(Instant.now().plus(TOKEN_VALID_MINUTES, ChronoUnit.MINUTES));
		repository.save(appUser);

		// the frontend uses HashRouter, so its routes are after the #
		String passwordResetLink = frontendUrl + "/#/reset-password?token=" + token;
		sendResetEmail(appUser.getEmail(), appUser.getUsername(), passwordResetLink);
	}

	/**
	 * @return false if the token is unknown or expired
	 */
	public boolean resetPassword(String token, String newPassword) {
		AppUser appUser = repository.findByResetToken(token);
		if (appUser == null || appUser.getResetTokenExpiresAt() == null
				|| appUser.getResetTokenExpiresAt().isBefore(Instant.now())) {
			return false;
		}

		appUser.setPasswordHash(passwordEncoder.encode(newPassword));
		appUser.setResetToken(null);
		appUser.setResetTokenExpiresAt(null);
		repository.save(appUser);
		return true;
	}

	private void sendResetEmail(String email, String username, String passwordResetLink) throws MessagingException {
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

		helper.setFrom(mailFrom);
		helper.setTo(email);

		String content = "<p>Hello,</p>" + "<p>You have requested to reset the password of user <b>" + username
				+ "</b> in the Bookstore.</p>" + "<p>Click the link below to reset your password. The link is valid for "
				+ TOKEN_VALID_MINUTES + " minutes.</p>" + "<p><a href=\"" + passwordResetLink
				+ "\">Change my password</a></p>";

		helper.setSubject("Bookstore password reset link");
		helper.setText(content, true);

		mailSender.send(message);
	}
}
