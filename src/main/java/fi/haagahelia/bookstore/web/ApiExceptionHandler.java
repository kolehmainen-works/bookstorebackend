package fi.haagahelia.bookstore.web;

import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import fi.haagahelia.bookstore.web.AuthController.MessageResponse;

// turns validation errors into {"message": "..."} like the other error answers
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<MessageResponse> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.sorted()
				.collect(Collectors.joining(", "));
		return ResponseEntity.badRequest().body(new MessageResponse("Invalid input: " + message + "."));
	}
}
