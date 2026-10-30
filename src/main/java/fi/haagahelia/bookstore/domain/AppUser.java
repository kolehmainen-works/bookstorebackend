package fi.haagahelia.bookstore.domain;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

// same table as kolehmainenbookstore, so its users keep working
@Entity(name = "users")
@Table(name = "users")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false, updatable = false)
	private Long id;

	@Column(name = "username", nullable = false, unique = true)
	private String username;

	@JsonIgnore
	@Column(name = "password", nullable = false)
	private String passwordHash;

	@Column(name = "email", nullable = false, unique = true)
	private String email;

	// ROLE_USER or ROLE_ADMIN
	@Column(name = "role", nullable = false)
	private String role;

	@JsonIgnore
	@Column(name = "reset_token")
	private String resetToken;

	@JsonIgnore
	@Column(name = "reset_token_expires_at")
	private Instant resetTokenExpiresAt;

	// the books this user has added; each user sees only these
	@JsonIgnore
	@OneToMany(mappedBy = "owner")
	private List<Book> books;

	public AppUser() {
	}

	public AppUser(String username, String passwordHash, String email, String role) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.email = email;
		this.role = role;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getResetToken() {
		return resetToken;
	}

	public void setResetToken(String resetToken) {
		this.resetToken = resetToken;
	}

	public Instant getResetTokenExpiresAt() {
		return resetTokenExpiresAt;
	}

	public void setResetTokenExpiresAt(Instant resetTokenExpiresAt) {
		this.resetTokenExpiresAt = resetTokenExpiresAt;
	}

	public List<Book> getBooks() {
		return books;
	}

	public void setBooks(List<Book> books) {
		this.books = books;
	}

}
