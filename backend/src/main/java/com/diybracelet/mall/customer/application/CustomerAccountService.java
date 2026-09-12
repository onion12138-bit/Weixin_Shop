package com.diybracelet.mall.customer.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.AddressView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.AdminAddressView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.CustomerSummary;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.ProfileView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.SaveAddressRequest;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.SaveProfileRequest;
import com.diybracelet.mall.customer.domain.CustomerAddress;
import com.diybracelet.mall.customer.domain.CustomerProfile;
import com.diybracelet.mall.customer.infrastructure.CustomerAddressMapper;
import com.diybracelet.mall.customer.infrastructure.CustomerProfileMapper;
import com.diybracelet.mall.file.domain.AssetFile;
import com.diybracelet.mall.file.infrastructure.AssetFileMapper;
import com.diybracelet.mall.identity.domain.CustomerIdentity;
import com.diybracelet.mall.identity.domain.CustomerIdentityResolver;
import com.diybracelet.mall.identity.infrastructure.CustomerIdentityMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerAccountService {
    private static final String ACTIVE = "ACTIVE";
    private static final String DELETED = "DELETED";
    private static final String DEFAULT_NICKNAME = "小丸子";

    private final CustomerIdentityResolver identityResolver;
    private final CustomerIdentityMapper identityMapper;
    private final CustomerProfileMapper profileMapper;
    private final CustomerAddressMapper addressMapper;
    private final AssetFileMapper assetFileMapper;

    public CustomerAccountService(
            CustomerIdentityResolver identityResolver,
            CustomerIdentityMapper identityMapper,
            CustomerProfileMapper profileMapper,
            CustomerAddressMapper addressMapper,
            AssetFileMapper assetFileMapper
    ) {
        this.identityResolver = identityResolver;
        this.identityMapper = identityMapper;
        this.profileMapper = profileMapper;
        this.addressMapper = addressMapper;
        this.assetFileMapper = assetFileMapper;
    }

    public ProfileView getProfile() {
        return profileView(identityResolver.requireCustomerIdentityId());
    }

    @Transactional
    public ProfileView saveProfile(SaveProfileRequest request) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        validateAvatar(request.avatarAssetId());
        CustomerProfile profile = profileMapper.selectById(customerId);
        if (profile == null) {
            profile = new CustomerProfile();
            profile.setCustomerIdentityId(customerId);
            profile.setNickname(request.nickname().trim());
            profile.setAvatarAssetId(request.avatarAssetId());
            profileMapper.insert(profile);
        } else {
            profile.setNickname(request.nickname().trim());
            profile.setAvatarAssetId(request.avatarAssetId());
            profileMapper.updateById(profile);
        }
        return profileView(customerId);
    }

    public List<AddressView> listAddresses() {
        Long customerId = identityResolver.requireCustomerIdentityId();
        return activeAddresses(customerId).stream().map(this::addressView).toList();
    }

    @Transactional
    public AddressView createAddress(SaveAddressRequest request) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        lockAddressOwner(customerId);
        List<CustomerAddress> existing = activeAddresses(customerId, true);
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault())
                || existing.isEmpty();
        if (makeDefault) clearDefaults(customerId);
        CustomerAddress address = new CustomerAddress();
        address.setCustomerIdentityId(customerId);
        applyAddress(address, request);
        address.setIsDefault(makeDefault);
        address.setStatus(ACTIVE);
        addressMapper.insert(address);
        return addressView(addressMapper.selectById(address.getId()));
    }

    @Transactional
    public AddressView updateAddress(Long addressId, SaveAddressRequest request) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        lockAddressOwner(customerId);
        CustomerAddress address = requireOwnedAddress(addressId, customerId, true);
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault());
        if (makeDefault) clearDefaults(customerId);
        applyAddress(address, request);
        address.setIsDefault(makeDefault || Boolean.TRUE.equals(address.getIsDefault()));
        addressMapper.updateById(address);
        return addressView(addressMapper.selectById(addressId));
    }

    @Transactional
    public AddressView setDefaultAddress(Long addressId) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        lockAddressOwner(customerId);
        CustomerAddress address = requireOwnedAddress(addressId, customerId, true);
        clearDefaults(customerId);
        address.setIsDefault(true);
        addressMapper.updateById(address);
        return addressView(addressMapper.selectById(addressId));
    }

    @Transactional
    public boolean deleteAddress(Long addressId) {
        Long customerId = identityResolver.requireCustomerIdentityId();
        lockAddressOwner(customerId);
        CustomerAddress address = requireOwnedAddress(addressId, customerId, true);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        address.setStatus(DELETED);
        address.setIsDefault(false);
        addressMapper.updateById(address);
        if (wasDefault) selectReplacementDefault(customerId);
        return true;
    }

    public List<CustomerSummary> listCustomers() {
        List<CustomerIdentity> identities = identityMapper.selectList(
                new LambdaQueryWrapper<CustomerIdentity>()
                        .eq(CustomerIdentity::getStatus, ACTIVE)
                        .orderByDesc(CustomerIdentity::getCreatedAt)
        );
        return customerSummaries(identities);
    }

    public List<CustomerSummary> searchCustomersByPhone(
            String phoneKeyword,
            int limit
    ) {
        String normalized = phoneKeyword == null ? "" : phoneKeyword.trim();
        List<Long> customerIds = addressMapper.searchCustomerIdentityIdsByPhone(
                normalized,
                limit
        );
        if (customerIds.isEmpty()) return List.of();
        Map<Long, CustomerIdentity> identitiesById = identityMapper
                .selectBatchIds(customerIds)
                .stream()
                .filter(identity -> ACTIVE.equals(identity.getStatus()))
                .collect(Collectors.toMap(
                        CustomerIdentity::getId,
                        Function.identity()
                ));
        List<CustomerIdentity> identities = customerIds.stream()
                .map(identitiesById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return customerSummaries(identities);
    }

    private List<CustomerSummary> customerSummaries(
            List<CustomerIdentity> identities
    ) {
        if (identities.isEmpty()) return List.of();
        Map<Long, CustomerProfile> profiles = profileMapper.selectBatchIds(
                identities.stream().map(CustomerIdentity::getId).toList()
        ).stream().collect(Collectors.toMap(
                CustomerProfile::getCustomerIdentityId,
                Function.identity()
        ));
        List<Long> customerIds = identities.stream()
                .map(CustomerIdentity::getId)
                .toList();
        List<CustomerAddress> allAddresses = addressMapper.selectList(
                new LambdaQueryWrapper<CustomerAddress>()
                        .in(CustomerAddress::getCustomerIdentityId, customerIds)
                        .orderByDesc(CustomerAddress::getIsDefault)
                        .orderByDesc(CustomerAddress::getUpdatedAt)
        );
        Map<Long, CustomerAddress> primaryAddresses = allAddresses.stream()
                .filter(address -> ACTIVE.equals(address.getStatus()))
                .collect(Collectors.toMap(
                CustomerAddress::getCustomerIdentityId,
                Function.identity(),
                (first, ignored) -> first
        ));
        Map<Long, Long> addressCounts = allAddresses.stream().collect(
                Collectors.groupingBy(
                        CustomerAddress::getCustomerIdentityId,
                        Collectors.counting()
                )
        );
        return identities.stream().map(identity -> {
            CustomerProfile profile = profiles.get(identity.getId());
            CustomerAddress address = primaryAddresses.get(identity.getId());
            String nickname = profile == null ? defaultNickname(identity.getId())
                    : profile.getNickname();
            return new CustomerSummary(
                    identity.getId(),
                    nickname,
                    profile == null ? null : avatarUrl(profile.getAvatarAssetId()),
                    address == null ? "" : address.getPhone(),
                    address == null ? "" : address.getRegion() + " " + address.getDetail(),
                    addressCounts.getOrDefault(identity.getId(), 0L),
                    identity.getSource(),
                    identity.getCreatedAt()
            );
        }).toList();
    }

    public List<AdminAddressView> listAdminAddresses(
            String keyword,
            String status,
            Long customerIdentityId
    ) {
        LambdaQueryWrapper<CustomerAddress> query =
                new LambdaQueryWrapper<CustomerAddress>()
                        .orderByDesc(CustomerAddress::getUpdatedAt);
        if (customerIdentityId != null) {
            query.eq(
                    CustomerAddress::getCustomerIdentityId,
                    customerIdentityId
            );
        }
        if (status != null && !status.isBlank()) {
            validateAddressStatus(status);
            query.eq(CustomerAddress::getStatus, status);
        }
        List<CustomerAddress> addresses = addressMapper.selectList(query);
        Map<Long, String> nicknames = listCustomers().stream().collect(
                Collectors.toMap(CustomerSummary::id, CustomerSummary::nickname)
        );
        String normalized = keyword == null ? ""
                : keyword.trim().toLowerCase(Locale.ROOT);
        return addresses.stream()
                .map(address -> adminAddressView(
                        address,
                        nicknames.getOrDefault(
                                address.getCustomerIdentityId(),
                                defaultNickname(address.getCustomerIdentityId())
                        )
                ))
                .filter(view -> normalized.isEmpty()
                        || contains(view.customerNickname(), normalized)
                        || contains(view.receiverName(), normalized)
                        || contains(view.phone(), normalized)
                        || contains(view.region(), normalized)
                        || contains(view.detail(), normalized))
                .toList();
    }

    @Transactional
    public AdminAddressView changeAddressStatus(Long addressId, String status) {
        validateAddressStatus(status);
        CustomerAddress address = addressMapper.selectById(addressId);
        if (address == null) notFound("收货地址不存在");
        lockAddressOwner(address.getCustomerIdentityId());
        address = requireOwnedAddress(addressId, address.getCustomerIdentityId(), false);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        address.setStatus(status);
        if (DELETED.equals(status)) address.setIsDefault(false);
        addressMapper.updateById(address);
        if (DELETED.equals(status) && wasDefault) {
            selectReplacementDefault(address.getCustomerIdentityId());
        } else if (ACTIVE.equals(status)
                && activeAddresses(address.getCustomerIdentityId(), true).stream()
                .noneMatch(item -> Boolean.TRUE.equals(item.getIsDefault()))) {
            address.setIsDefault(true);
            addressMapper.updateById(address);
        }
        String nickname = profileView(address.getCustomerIdentityId()).nickname();
        return adminAddressView(addressMapper.selectById(addressId), nickname);
    }

    private ProfileView profileView(Long customerId) {
        CustomerProfile profile = profileMapper.selectById(customerId);
        if (profile == null) {
            return new ProfileView(customerId, defaultNickname(customerId), null, null);
        }
        return new ProfileView(
                customerId,
                profile.getNickname(),
                profile.getAvatarAssetId(),
                avatarUrl(profile.getAvatarAssetId())
        );
    }

    private String defaultNickname(Long customerId) {
        return customerId == null ? DEFAULT_NICKNAME : DEFAULT_NICKNAME;
    }

    private String avatarUrl(Long assetId) {
        if (assetId == null) return null;
        AssetFile asset = assetFileMapper.selectById(assetId);
        return asset != null && ACTIVE.equals(asset.getStatus())
                ? asset.getPublicUrl() : null;
    }

    private void validateAvatar(Long assetId) {
        if (assetId == null) return;
        AssetFile asset = assetFileMapper.selectById(assetId);
        if (asset == null || !ACTIVE.equals(asset.getStatus())) {
            notFound("头像素材不存在或已停用");
        }
        if (asset.getContentType() == null
                || !asset.getContentType().startsWith("image/")) {
            badRequest("头像必须是图片文件");
        }
    }

    private List<CustomerAddress> activeAddresses(Long customerId) {
        return activeAddresses(customerId, false);
    }

    private List<CustomerAddress> activeAddresses(Long customerId, boolean forUpdate) {
        return addressMapper.selectList(
                new LambdaQueryWrapper<CustomerAddress>()
                        .eq(CustomerAddress::getCustomerIdentityId, customerId)
                        .eq(CustomerAddress::getStatus, ACTIVE)
                        .orderByDesc(CustomerAddress::getIsDefault)
                        .orderByDesc(CustomerAddress::getUpdatedAt)
                        .last(forUpdate, "for update")
        );
    }

    private void lockAddressOwner(Long customerId) {
        if (identityMapper.selectByIdForUpdate(customerId) == null) {
            notFound("顾客不存在");
        }
    }

    private CustomerAddress requireOwnedAddress(
            Long addressId,
            Long customerId,
            boolean activeOnly
    ) {
        LambdaQueryWrapper<CustomerAddress> query =
                new LambdaQueryWrapper<CustomerAddress>()
                        .eq(CustomerAddress::getId, addressId)
                        .eq(CustomerAddress::getCustomerIdentityId, customerId);
        if (activeOnly) query.eq(CustomerAddress::getStatus, ACTIVE);
        query.last("for update");
        CustomerAddress address = addressMapper.selectOne(query);
        if (address == null) notFound("收货地址不存在");
        return address;
    }

    private void clearDefaults(Long customerId) {
        for (CustomerAddress current : activeAddresses(customerId, true)) {
            if (Boolean.TRUE.equals(current.getIsDefault())) {
                current.setIsDefault(false);
                addressMapper.updateById(current);
            }
        }
    }

    private void selectReplacementDefault(Long customerId) {
        List<CustomerAddress> remaining = activeAddresses(customerId, true);
        if (!remaining.isEmpty()) {
            CustomerAddress replacement = remaining.get(0);
            replacement.setIsDefault(true);
            addressMapper.updateById(replacement);
        }
    }

    private void applyAddress(CustomerAddress address, SaveAddressRequest request) {
        address.setReceiverName(request.receiverName().trim());
        address.setPhone(request.phone().trim());
        address.setRegion(request.region().trim());
        address.setDetail(request.detail().trim());
    }

    private AddressView addressView(CustomerAddress address) {
        return new AddressView(
                address.getId(), address.getReceiverName(), address.getPhone(),
                address.getRegion(), address.getDetail(), address.getIsDefault(),
                address.getStatus(), address.getCreatedAt(), address.getUpdatedAt()
        );
    }

    private AdminAddressView adminAddressView(
            CustomerAddress address,
            String nickname
    ) {
        return new AdminAddressView(
                address.getId(), address.getCustomerIdentityId(), nickname,
                address.getReceiverName(), address.getPhone(), address.getRegion(),
                address.getDetail(), address.getIsDefault(), address.getStatus(),
                address.getUpdatedAt()
        );
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private void validateAddressStatus(String status) {
        if (!ACTIVE.equals(status) && !DELETED.equals(status)) {
            badRequest("地址状态只能是 ACTIVE 或 DELETED");
        }
    }

    private void notFound(String message) {
        throw new BusinessException(ErrorCode.COMMON_NOT_FOUND, message);
    }

    private void badRequest(String message) {
        throw new BusinessException(ErrorCode.COMMON_BAD_REQUEST, message);
    }
}
