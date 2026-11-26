package com.fitlink.entitlement.controller;

import com.fitlink.entitlement.dto.EntitlementRegisterRequest;
import com.fitlink.entitlement.dto.EntitlementCardResponse;
import com.fitlink.entitlement.service.EntitlementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
class EntitlementController {

	private final EntitlementService entitlements;

	EntitlementController(EntitlementService entitlements) {
		this.entitlements = entitlements;
	}

	@PostMapping("/instructor/members/{id}/entitlements")
	@ResponseStatus(HttpStatus.CREATED)
	EntitlementCardResponse register(@AuthenticationPrincipal Long instructorId, @PathVariable long id,
			@RequestBody JsonNode body) {
		return EntitlementCardResponse.of(entitlements.register(instructorId, id, EntitlementRegisterRequest.from(body)));
	}

}
