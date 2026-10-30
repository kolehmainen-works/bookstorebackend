package fi.haagahelia.bookstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.haagahelia.bookstore.domain.AppUser;
import fi.haagahelia.bookstore.domain.AppUserRepository;

// H2 instead of the server's MySQL, so the tests run anywhere
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"server.ssl.enabled=false" })
@AutoConfigureMockMvc
class BookstoreApplicationTests {

	private static final AtomicInteger counter = new AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String login(String username, String password) throws Exception {
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + objectMapper.readTree(body).get("token").asText();
	}

	// signs up a new user and returns their Authorization header
	private String signup(String username) throws Exception {
		String body = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(username, username + "@example.com", "password1", "password1")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + objectMapper.readTree(body).get("token").asText();
	}

	private static String uniqueName(String prefix) {
		return prefix + counter.incrementAndGet();
	}

	private static String signupJson(String username, String email, String password, String passwordCheck) {
		return "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password
				+ "\",\"passwordCheck\":\"" + passwordCheck + "\"}";
	}

	private long firstCategoryId(String token) throws Exception {
		String categories = mockMvc.perform(get("/api/categories").header("Authorization", token))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(categories).get(0).get("id").asLong();
	}

	private long addBook(String token, String title) throws Exception {
		String created = mockMvc.perform(post("/api/books").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"" + title + "\",\"author\":\"Frank Herbert\",\"isbn\":\"9780441172719\","
						+ "\"publicationYear\":1965,\"price\":12.5,\"category\":{\"id\":" + firstCategoryId(token) + "}}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(created).get("id").asLong();
	}

	@Test
	void booksNeedAToken() throws Exception {
		mockMvc.perform(get("/api/books")).andExpect(status().isUnauthorized());
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"user\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginReturnsRoles() throws Exception {
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"admin\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		JsonNode json = objectMapper.readTree(body);
		assertThat(json.get("roles").get(0).asText()).isEqualTo("ROLE_ADMIN");
		assertThat(json.get("username").asText()).isEqualTo("admin");
	}

	@Test
	void signupCreatesAUserWhoCanSignIn() throws Exception {
		String username = uniqueName("reader");
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(username, username + "@example.com", "password1", "password1")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.username").value(username))
				.andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));

		login(username, "password1");
		// a new user starts with an empty book list
		mockMvc.perform(get("/api/books").header("Authorization", login(username, "password1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void signupRejectsTakenNamesAndBadInput() throws Exception {
		String username = uniqueName("taken");
		signup(username);

		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(username, "other-" + username + "@example.com", "password1", "password1")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Username is already taken."));
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(uniqueName("other"), username + "@example.com", "password1", "password1")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Email is already in use."));
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(uniqueName("mismatch"), "m@example.com", "password1", "password2")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(uniqueName("short"), "s@example.com", "short", "short")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid input: password size must be between 7 and 30."));
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(uniqueName("bademail"), "not-an-email", "password1", "password1")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void eachUserSeesOnlyTheirOwnBooks() throws Exception {
		String alice = signup(uniqueName("alice"));
		String bob = signup(uniqueName("bob"));
		long bookId = addBook(alice, "Alice's Dune");

		mockMvc.perform(get("/api/books").header("Authorization", alice))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("Alice's Dune"));
		mockMvc.perform(get("/api/books").header("Authorization", bob))
				.andExpect(jsonPath("$.length()").value(0));

		// another user's book looks like it does not exist
		mockMvc.perform(get("/api/books/" + bookId).header("Authorization", bob)).andExpect(status().isNotFound());
		mockMvc.perform(put("/api/books/" + bookId).header("Authorization", bob).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Stolen\",\"author\":\"x\",\"isbn\":\"1\",\"publicationYear\":2000}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/books/" + bookId).header("Authorization", bob)).andExpect(status().isNotFound());

		mockMvc.perform(get("/api/books/" + bookId).header("Authorization", alice))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Alice's Dune"));
	}

	@Test
	void ownerCanEditAndDeleteTheirBook() throws Exception {
		String username = uniqueName("owner");
		String token = signup(username);
		long bookId = addBook(token, "Dune");

		mockMvc.perform(put("/api/books/" + bookId).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Dune Messiah\",\"author\":\"Frank Herbert\",\"isbn\":\"9780441172696\","
						+ "\"publicationYear\":1969,\"price\":13.5}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Dune Messiah"))
				.andExpect(jsonPath("$.ownerUsername").value(username));

		mockMvc.perform(delete("/api/books/" + bookId).header("Authorization", token))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/books/" + bookId).header("Authorization", token))
				.andExpect(status().isNotFound());
	}

	@Test
	void demoBooksBelongToTheDemoUser() throws Exception {
		mockMvc.perform(get("/api/books").header("Authorization", login("user", "user")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].ownerUsername").value("user"))
				.andExpect(jsonPath("$[0].category.name").exists());
	}

	@Test
	void adminSeesAndManagesEveryonesBooks() throws Exception {
		String username = uniqueName("carol");
		long bookId = addBook(signup(username), "Carol's book");
		String admin = login("admin", "admin");

		String books = mockMvc.perform(get("/api/books").header("Authorization", admin))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		boolean found = false;
		for (JsonNode book : objectMapper.readTree(books)) {
			if (book.get("id").asLong() == bookId) {
				found = true;
				assertThat(book.get("ownerUsername").asText()).isEqualTo(username);
			}
		}
		assertThat(found).isTrue();

		mockMvc.perform(delete("/api/books/" + bookId).header("Authorization", admin))
				.andExpect(status().isNoContent());
	}

	@Test
	void userCanDeleteTheirAccount() throws Exception {
		String username = uniqueName("leaving");
		String token = signup(username);
		addBook(token, "Left behind");

		mockMvc.perform(delete("/api/users/me").header("Authorization", token)).andExpect(status().isNoContent());

		assertThat(appUserRepository.findByUsername(username)).isNull();
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"password1\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void forgotPasswordDoesNotRevealUnknownEmails() throws Exception {
		mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody@example.com\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void passwordCanBeResetWithAValidToken() throws Exception {
		AppUser user = appUserRepository.findByUsername("user");
		user.setResetToken("testtoken");
		user.setResetTokenExpiresAt(Instant.now().plusSeconds(600));
		appUserRepository.save(user);

		mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"testtoken\",\"password\":\"different1\",\"passwordCheck\":\"different2\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"testtoken\",\"password\":\"newpassword\",\"passwordCheck\":\"newpassword\"}"))
				.andExpect(status().isOk());

		login("user", "newpassword");

		// the token works only once
		mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"testtoken\",\"password\":\"another1\",\"passwordCheck\":\"another1\"}"))
				.andExpect(status().isBadRequest());

		// put the demo password back for the other tests
		user = appUserRepository.findByUsername("user");
		user.setPasswordHash(passwordEncoder.encode("user"));
		appUserRepository.save(user);
	}

}
