package com.diybracelet.mall.customer.api;

import com.diybracelet.mall.customer.api.dto.CustomerDtos.AdminAddressView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.ChangeStatusRequest;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.CustomerSummary;
import com.diybracelet.mall.customer.application.CustomerAccountService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminCustomerAccountController {
    private final CustomerAccountService accountService;

    public AdminCustomerAccountController(CustomerAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/customers")
    public ApiResponse<List<CustomerSummary>> listCustomers() {
        return success(accountService.listCustomers());
    }

    @GetMapping("/customers/search")
    public ApiResponse<List<CustomerSummary>> searchCustomers(
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return success(accountService.searchCustomersByPhone(phone, limit));
    }

    @GetMapping("/customer-addresses")
    public ApiResponse<List<AdminAddressView>> listAddresses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @Min(1) Long customerIdentityId
    ) {
        return success(accountService.listAdminAddresses(
                keyword,
                status,
                customerIdentityId
        ));
    }

    @PatchMapping("/customer-addresses/{id}/status")
    public ApiResponse<AdminAddressView> changeAddressStatus(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody ChangeStatusRequest request
    ) {
        return success(accountService.changeAddressStatus(id, request.status()));
    }

    private <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, UUID.randomUUID().toString());
    }
}
