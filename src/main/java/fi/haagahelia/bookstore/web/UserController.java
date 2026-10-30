package fi.haagahelia.bookstore.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fi.haagahelia.bookstore.domain.AppUser;
import fi.haagahelia.bookstore.domain.AppUserRepository;
import fi.haagahelia.bookstore.domain.BookRepository;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final AppUserRepository appUserRepository;
	private final BookRepository bookRepository;

	public UserController(AppUserRepository appUserRepository, BookRepository bookRepository) {
		this.appUserRepository = appUserRepository;
		this.bookRepository = bookRepository;
	}

	// deletes the signed-in user's account together with their books
	@Transactional
	@DeleteMapping("/me")
	public ResponseEntity<Void> deleteMyAccount(Authentication authentication) {
		AppUser user = appUserRepository.findByUsername(authentication.getName());
		if (user == null) {
			return ResponseEntity.notFound().build();
		}
		bookRepository.deleteAll(bookRepository.findByOwner_Username(user.getUsername()));
		appUserRepository.delete(user);
		return ResponseEntity.noContent().build();
	}
}
