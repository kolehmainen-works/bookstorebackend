package com.viimeiset.bookstore.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viimeiset.bookstore.domain.Book;
import com.viimeiset.bookstore.domain.BookRepository;
import com.viimeiset.bookstore.domain.Category;
import com.viimeiset.bookstore.domain.CategoryRepository;

import jakarta.validation.Valid;

/**
 * The services of kolehmainenbookstore's BookController as a JSON API:
 * list, show, add, edit and (admins only) delete books.
 */
@RestController
@RequestMapping("/api")
public class BookController {

	private final BookRepository bookRepository;
	private final CategoryRepository categoryRepository;

	public BookController(BookRepository bookRepository, CategoryRepository categoryRepository) {
		this.bookRepository = bookRepository;
		this.categoryRepository = categoryRepository;
	}

	@GetMapping("/books")
	public List<Book> listBooks() {
		return (List<Book>) bookRepository.findAll();
	}

	@GetMapping("/books/{id}")
	public ResponseEntity<Book> findBook(@PathVariable Long id) {
		return ResponseEntity.of(bookRepository.findById(id));
	}

	@PostMapping("/books")
	public ResponseEntity<Book> addBook(@Valid @RequestBody Book book) {
		book.setId(null);
		book.setCategory(resolveCategory(book.getCategory()));
		return ResponseEntity.status(HttpStatus.CREATED).body(bookRepository.save(book));
	}

	@PutMapping("/books/{id}")
	public ResponseEntity<Book> editBook(@PathVariable Long id, @Valid @RequestBody Book book) {
		if (!bookRepository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		book.setId(id);
		book.setCategory(resolveCategory(book.getCategory()));
		return ResponseEntity.ok(bookRepository.save(book));
	}

	@Secured("ROLE_ADMIN")
	@DeleteMapping("/books/{id}")
	public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
		if (!bookRepository.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		bookRepository.deleteById(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/categories")
	public Iterable<Category> listCategories() {
		return categoryRepository.findAll();
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
