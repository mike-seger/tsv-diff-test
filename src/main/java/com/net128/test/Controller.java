package com.net128.test;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("api")
public class Controller {
	public Controller(Service service) { this.service = service; }
	private final Service service;

	@CrossOrigin(origins = "*")
	@PostMapping(value = "/zipdiff", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
	public ResponseEntity<String> zipDiff(
			@RequestParam MultipartFile file1,
			@RequestParam MultipartFile file2,
			@RequestParam(required = false, defaultValue = "3") int contextLines) {
		try {
			var result = service.performGitDiff(file1.getInputStream(), file2.getInputStream(), contextLines);
			return ResponseEntity.ok(result);
		} catch (IOException e) {
			e.printStackTrace();
			return ResponseEntity.status(500).body("Error reading or unzipping files:" + e.getMessage());
		}
	}

	@CrossOrigin(origins = "*")
	@GetMapping(value = "/zipdiff/{id1}/{id2}")
	public ResponseEntity<String> zipDiffById(
			@PathVariable String id1,
			@PathVariable String id2,
			@RequestParam(required = false, defaultValue = "0") int contextLines) {
		try {
			var result = service.performGitDiff(id1, id2, contextLines);
			return ResponseEntity.ok(result);
		} catch (IOException e) {
			e.printStackTrace();
			return ResponseEntity.status(500).body("Error reading or unzipping files:" + e.getMessage());
		}
	}
}