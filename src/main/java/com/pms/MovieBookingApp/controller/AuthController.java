package com.pms.MovieBookingApp.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pms.MovieBookingApp.model.ERole;
import com.pms.MovieBookingApp.model.Role;
import com.pms.MovieBookingApp.model.User;
import com.pms.MovieBookingApp.payload.request.LoginRequest;
import com.pms.MovieBookingApp.payload.request.SignupRequest;
import com.pms.MovieBookingApp.payload.response.JwtResponse;
import com.pms.MovieBookingApp.payload.response.MessageResponse;
import com.pms.MovieBookingApp.repository.RoleRepo;
import com.pms.MovieBookingApp.repository.UserRepo;
import com.pms.MovieBookingApp.security.jwt.JwtUtils;
import com.pms.MovieBookingApp.security.services.UserDetailsImpl;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/moviebooking")
@Slf4j
public class AuthController {
	@Autowired
	AuthenticationManager authenticationManager;

	@Autowired
	UserRepo userRepository;

	@Autowired
	RoleRepo roleRepository;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JwtUtils jwtUtils;

	@PostMapping("/login")
	@Operation(summary = "login")
	public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(loginRequest.getLoginId(), loginRequest.getPassword()));

		SecurityContextHolder.getContext().setAuthentication(authentication);
		String jwt = jwtUtils.generateJwtToken(authentication);

		UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
		List<String> roles = userDetails.getAuthorities().stream().map(item -> item.getAuthority())
				.collect(Collectors.toList());

		return ResponseEntity.ok(
				new JwtResponse(jwt, userDetails.get_id(), userDetails.getUsername(), userDetails.getEmail(), roles));
	}

	@PostMapping("/register")
	@Operation(summary = "new registration")
	public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
		if (userRepository.existsByLoginId(signUpRequest.getLoginId())) {
			return ResponseEntity.badRequest().body(new MessageResponse("Error: LoginId is already taken!"));
		}

		if (userRepository.existsByEmail(signUpRequest.getEmail())) {
			return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
		}

		// Create new user's account
		User user = new User(signUpRequest.getLoginId(), signUpRequest.getFirstName(), signUpRequest.getLastName(),
				signUpRequest.getEmail(), signUpRequest.getContactNumber(),
				encoder.encode(signUpRequest.getPassword()));

		Set<String> strRoles = signUpRequest.getRoles();
		Set<Role> roles = new HashSet<>();

		String errorMessegae = "Error: Role is not found.";

		if (strRoles == null) {
			Role userRole = roleRepository.findByName(ERole.ROLE_USER)
					.orElseThrow(() -> new RuntimeException(errorMessegae));
			roles.add(userRole);
		} else {
			strRoles.forEach(role -> {
				switch (role) {
				case "admin":
					Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
							.orElseThrow(() -> new RuntimeException(errorMessegae));
					roles.add(adminRole);

					break;
				case "guest":
					Role modRole = roleRepository.findByName(ERole.ROLE_GUEST)
							.orElseThrow(() -> new RuntimeException(errorMessegae));
					roles.add(modRole);

					break;
				default:
					Role userRole = roleRepository.findByName(ERole.ROLE_USER)
							.orElseThrow(() -> new RuntimeException(errorMessegae));
					roles.add(userRole);
				}
			});
		}

		user.setRoles(roles);
		userRepository.save(user);

		return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
	}
}