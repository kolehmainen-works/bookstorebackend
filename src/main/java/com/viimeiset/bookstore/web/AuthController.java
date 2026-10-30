package com.viimeiset.bookstore.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viimeiset.bookstore.service.JwtService;
import com.viimeiset.bookstore.service.PasswordResetService;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final PasswordResetService passwordResetService;

	public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
			PasswordResetService passwordResetService) {
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
		this.passwordResetService = passwordResetService;
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	public record LoginResponse(String token, Instant expiresAt, String username, List<String> roles) {
	}

	public record ForgotPasswordRequest(@NotBlank @Email String email) {
	}

	public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 7, max = 30) String password,
			@NotBlank String passwordCheck) {
	}

	public record MessageResponse(String message) {
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
		try {
			Authentication authentication = authenticationManager
					.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
			JwtService.IssuedToken issued = jwtService.createToken(authentication);
			return ResponseEntity.ok(
					new LoginResponse(issued.token(), issued.expiresAt(), authentication.getName(), issued.roles()));
		} catch (AuthenticationException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new MessageResponse("Invalid username or password."));
		}
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		try {
			passwordResetService.sendResetLink(request.email());
		} catch (MessagingException | MailException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new MessageResponse("Error while sending email."));
		}
		// same answer whether or not the email exists
		return ResponseEntity.ok(new MessageResponse(
				"If this email belongs to a user, we have sent a reset link to it. Please check your email."));
	}

	@PostMapping("/reset-password")
	public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		if (!request.password().equals(request.passwordCheck())) {
			return ResponseEntity.badRequest().body(new MessageResponse("Passwords do not match."));
		}
		if (!passwordResetService.resetPassword(request.token(), request.password())) {
			return ResponseEntity.badRequest()
					.body(new MessageResponse("The reset link is invalid or has expired. Please request a new one."));
		}
		return ResponseEntity.ok(new MessageResponse("Your password has been changed. You can now sign in."));
	}
}
