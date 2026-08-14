package pocketpaystore.pocketpay_core.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import pocketpaystore.pocketpay_core.auth.dto.request.LoginRequest;
import pocketpaystore.pocketpay_core.auth.dto.response.LoginResponse;
import pocketpaystore.pocketpay_core.auth.service.AuthService;
import pocketpaystore.pocketpay_core.common.response.ApiResponse;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
	}

}
