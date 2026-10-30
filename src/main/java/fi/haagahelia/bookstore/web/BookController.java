package fi.haagahelia.bookstore.web;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import fi.haagahelia.bookstore.domain.AppUser;
import fi.haagahelia.bookstore.domain.AppUserRepository;
import fi.haagahelia.bookstore.domain.Book;
import fi.haagahelia.bookstore.domain.BookRepository;
import fi.haagahelia.bookstore.domain.Category;
import fi.haagahelia.bookstore.domain.CategoryRepository;

import jakarta.validation.Valid;

/**
 * The services of kolehmainenbookstore's BookController as a JSON API.
 * Every user sees, edits and deletes only their own books; admins see all.
 * A book of another user answers 404, as if it did not exist.
 */
@RestController
@RequestMapping("/api")
public class BookController {

	private final BookRepository bookRepository;
	private final CategoryRepository categoryRepository;
	private final AppUserRepository appUserRepository;

	public BookController(BookRepository bookRepository, CategoryRepository categoryRepository,
			AppUserRepository appUserRepository) {
		this.bookRepository = bookRepository;
		this.categoryRepository = categoryRepository;
		this.appUserRepository = appUserRepository;
	}

	@GetMapping("/books")
	public List<Book> listBooks(Authentication authentication) {
		if (isAdmin(authentication)) {
			return (List<Book>) bookRepository.findAll();
		}
		return bookRepository.findByOwner_Username(authentication.getName());
	}

	@GetMapping("/books/{id}")
	public ResponseEntity<Book> findBook(@PathVariable Long id, Authentication authentication) {
		return ResponseEntity.of(findAccessibleBook(id, authentication));
	}

	@PostMapping("/books")
	public ResponseEntity<Book> addBook(@Valid @RequestBody Book book, Authentication authentication) {
		AppUser owner = appUserRepository.findByUsername(authentication.getName());
		if (owner == null) {
			// the account was deleted after the token was issued
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		book.setId(null);
		book.setOwner(owner);
		book.setCategory(resolveCategory(book.getCategory()));
		return ResponseEntity.status(HttpStatus.CREATED).body(bookRepository.save(book));
	}

	@PutMapping("/books/{id}")
	public ResponseEntity<Book> editBook(@PathVariable Long id, @Valid @RequestBody Book book,
			Authentication authentication) {
		Optional<Book> existing = findAccessibleBook(id, authentication);
		if (existing.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		book.setId(id);
		// editing never changes who owns the book
		book.setOwner(existing.get().getOwner());
		book.setCategory(resolveCategory(book.getCategory()));
		return ResponseEntity.ok(bookRepository.save(book));
	}

	@DeleteMapping("/books/{id}")
	public ResponseEntity<Void> deleteBook(@PathVariable Long id, Authentication authentication) {
		Optional<Book> existing = findAccessibleBook(id, authentication);
		if (existing.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		bookRepository.delete(existing.get());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/categories")
	public Iterable<Category> listCategories() {
		return categoryRepository.findAll();
	}

	private Optional<Book> findAccessibleBook(Long id, Authentication authentication) {
		if (isAdmin(authentication)) {
			return bookRepository.findById(id);
		}
		return bookRepository.findByIdAndOwner_Username(id, authentication.getName());
	}

	private static boolean isAdmin(Authentication authentication) {
		return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
	}

	// the client sends only {"id": ...} for the category, load the real one
	private Category resolveCategory(Category category) {
		if (category == null || category.getId() == null) {
			return null;
		}
		return categoryRepository.findById(category.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category"));
	}
}
