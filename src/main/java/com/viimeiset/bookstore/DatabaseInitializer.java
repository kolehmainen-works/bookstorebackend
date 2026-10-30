package com.viimeiset.bookstore;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.viimeiset.bookstore.domain.AppUser;
import com.viimeiset.bookstore.domain.AppUserRepository;
import com.viimeiset.bookstore.domain.Book;
import com.viimeiset.bookstore.domain.BookRepository;
import com.viimeiset.bookstore.domain.Category;
import com.viimeiset.bookstore.domain.CategoryRepository;

/**
 * Same demo data as kolehmainenbookstore. Only added to an empty database,
 * because ddl-auto=update keeps the data between restarts.
 */
@Configuration
public class DatabaseInitializer {

	@Bean
	public CommandLineRunner initializeDatabase(BookRepository bookRepository, CategoryRepository categoryRepository,
			AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
		return (args) -> {
			if (appUserRepository.count() == 0) {
				appUserRepository.save(
						new AppUser("user", passwordEncoder.encode("user"), "user1@haagahelia.fi", "ROLE_USER"));
				appUserRepository.save(
						new AppUser("admin", passwordEncoder.encode("admin"), "admin1@haagahelia.fi", "ROLE_ADMIN"));
			}

			if (categoryRepository.count() > 0 || bookRepository.count() > 0) {
				return;
			}

			Category fantasy = categoryRepository.save(new Category("Fantasy"));
			Category dystopian = categoryRepository.save(new Category("Dystopian"));
			Category historical = categoryRepository.save(new Category("Historical fiction"));
			Category literary = categoryRepository.save(new Category("Literary fiction"));
			Category nonfiction = categoryRepository.save(new Category("Non-fiction"));

			bookRepository.save(new Book("The Great Gatsby", "F. Scott Fitzgerald", "9780743273565", 1925, 10.99, literary));
			bookRepository.save(new Book("To Kill a Mockingbird", "Harper Lee", "9780061120083", 1960, 13.90, literary));
			bookRepository.save(new Book("The Catcher in the Rye", "J.D. Salinger", "9780316769488", 1951, 11.95, literary));
			bookRepository.save(new Book("A Farewell to Arms", "Ernest Hemingway", "9780099910107", 1929, 12.99, historical));
			bookRepository.save(new Book("The Book Thief", "Markus Zusak", "9780375842207", 2005, 14.50, historical));
			bookRepository.save(new Book("Nineteen Eighty-Four", "George Orwell", "9780451524935", 1949, 11.50, dystopian));
			bookRepository.save(new Book("Brave New World", "Aldous Huxley", "9780060850524", 1932, 12.50, dystopian));
			bookRepository.save(new Book("Fahrenheit 451", "Ray Bradbury", "9781451673319", 1953, 10.90, dystopian));
			bookRepository.save(new Book("The Hobbit", "J.R.R. Tolkien", "9780261103344", 1937, 14.99, fantasy));
			bookRepository.save(new Book("A Game of Thrones", "George R.R. Martin", "9780553593716", 1996, 15.90, fantasy));
			bookRepository.save(new Book("The Name of the Wind", "Patrick Rothfuss", "9780756404741", 2007, 17.50, fantasy));
			bookRepository.save(new Book("Sapiens", "Yuval Noah Harari", "9780062316097", 2015, 16.99, nonfiction));
		};
	}
}
