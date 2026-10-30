package com.viimeiset.bookstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viimeiset.bookstore.domain.AppUser;
import com.viimeiset.bookstore.domain.AppUserRepository;

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
		return objectMapper.readTree(body).get("token").asText();
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
	void userCanListAddAndEditButNotDelete() throws Exception {
		String token = "Bearer " + login("user", "user");

		mockMvc.perform(get("/api/books").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].title").exists())
				.andExpect(jsonPath("$[0].category.name").exists());

		String categories = mockMvc.perform(get("/api/categories").header("Authorization", token))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		long categoryId = objectMapper.readTree(categories).get(0).get("id").asLong();

		String created = mockMvc.perform(post("/api/books").header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Dune\",\"author\":\"Frank Herbert\",\"isbn\":\"9780441172719\","
						+ "\"publicationYear\":1965,\"price\":12.5,\"category\":{\"id\":" + categoryId + "}}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.category.id").value(categoryId))
				.andReturn().getResponse().getContentAsString();
		long bookId = objectMapper.readTree(created).get("id").asLong();

		mockMvc.perform(put("/api/books/" + bookId).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Dune Messiah\",\"author\":\"Frank Herbert\",\"isbn\":\"9780441172696\","
						+ "\"publicationYear\":1969,\"price\":13.5,\"category\":{\"id\":" + categoryId + "}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Dune Messiah"));

		mockMvc.perform(delete("/api/books/" + bookId).header("Authorization", token))
				.andExpect(status().isForbidden());

		String admin = "Bearer " + login("admin", "admin");
		mockMvc.perform(delete("/api/books/" + bookId).header("Authorization", admin))
				.andExpect(status().isNoContent());
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
	void forgotPasswordDoesNotRevealUnknownEmails() throws Exception {
		mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"nobody@example.com\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void passwordCanBeResetWithAValidToken() throws Exception {
		AppUser user = appUserRepository.findByUsername("user");
		user.setResetToken("testtoken");
		user.setResetTokenExpiresAt(java.time.Instant.now().plusSeconds(600));
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
