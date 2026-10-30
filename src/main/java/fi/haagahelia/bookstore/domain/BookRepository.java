package fi.haagahelia.bookstore.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

public interface BookRepository extends CrudRepository<Book, Long> {
	List<Book> findByOwner_Username(String username);

	Optional<Book> findByIdAndOwner_Username(Long id, String username);

	List<Book> findByOwnerIsNull();
}
