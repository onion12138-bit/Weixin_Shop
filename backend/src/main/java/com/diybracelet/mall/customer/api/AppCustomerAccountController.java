package com.diybracelet.mall.customer.api;

import com.diybracelet.mall.customer.api.dto.CustomerDtos.AddressView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.ProfileView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.SaveAddressRequest;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.SaveProfileRequest;
import com.diybracelet.mall.customer.application.CustomerAccountService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/app")
public class AppCustomerAccountController {
    private final CustomerAccountService accountService;

    public AppCustomerAccountController(CustomerAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileView> getProfile() {
        return success(accountService.getProfile());
    }

    @PutMapping("/profile")
    public ApiResponse<ProfileView> saveProfile(
            @Valid @RequestBody SaveProfileRequest request
    ) {
        return success(accountService.saveProfile(request));
    }

    @GetMapping("/addresses")
    public ApiResponse<List<AddressView>> listAddresses() {
        return success(accountService.listAddresses());
    }

    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<AddressView>> createAddress(
            @Valid @RequestBody SaveAddressRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(accountService.createAddress(request)));
    }

    @PutMapping("/addresses/{id}")
    public ApiResponse<AddressView> updateAddress(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody SaveAddressRequest request
    ) {
        return success(accountService.updateAddress(id, request));
    }

    @PutMapping("/addresses/{id}/default")
    public ApiResponse<AddressView> setDefaultAddress(
            @PathVariable @Min(1) Long id
    ) {
        return success(accountService.setDefaultAddress(id));
    }

    @DeleteMapping("/addresses/{id}")
    public ApiResponse<Boolean> deleteAddress(@PathVariable @Min(1) Long id) {
        return success(accountService.deleteAddress(id));
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }
}
