package fi.haagahelia.bookstore.web;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fi.haagahelia.bookstore.domain.AppUser;
import fi.haagahelia.bookstore.domain.AppUserRepository;
import fi.haagahelia.bookstore.service.JwtService;
import fi.haagahelia.bookstore.service.PasswordResetService;

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
	private final AppUserRepository appUserRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
			PasswordResetService passwordResetService, AppUserRepository appUserRepository,
			PasswordEncoder passwordEncoder) {
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
		this.passwordResetService = passwordResetService;
		this.appUserRepository = appUserRepository;
		this.passwordEncoder = passwordEncoder;
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

	public record SignupRequest(@NotBlank @Size(min = 3, max = 30) String username, @NotBlank @Email String email,
			@NotBlank @Size(min = 7, max = 30) String password, @NotBlank String passwordCheck) {
	}

	public record MessageResponse(String message) {
	}

	// creates a ROLE_USER account and signs the new user in
	@PostMapping("/signup")
	public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
		String username = request.username().trim();
		String email = request.email().trim();
		if (!request.password().equals(request.passwordCheck())) {
			return ResponseEntity.badRequest().body(new MessageResponse("Passwords do not match."));
		}
		if (appUserRepository.findByUsername(username) != null) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponse("Username is already taken."));
		}
		if (appUserRepository.findByEmail(email) != null) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponse("Email is already in use."));
		}

		try {
			appUserRepository.save(new AppUser(username, passwordEncoder.encode(request.password()), email, "ROLE_USER"));
		} catch (DataIntegrityViolationException e) {
			// someone signed up with the same name or email at the same moment
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new MessageResponse("Username or email is already in use."));
		}

		Authentication authentication = authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(username, request.password()));
		JwtService.IssuedToken issued = jwtService.createToken(authentication);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new LoginResponse(issued.token(), issued.expiresAt(), authentication.getName(), issued.roles()));
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
