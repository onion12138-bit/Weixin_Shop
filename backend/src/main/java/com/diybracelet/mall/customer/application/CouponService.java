package com.diybracelet.mall.customer.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.BatchGrantCouponRequest;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.BatchGrantCouponResult;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.CouponTemplateView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.CustomerCouponView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.GrantCouponRequest;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.SaveCouponTemplateRequest;
import com.diybracelet.mall.customer.domain.CouponTemplate;
import com.diybracelet.mall.customer.domain.CustomerCoupon;
import com.diybracelet.mall.customer.infrastructure.CouponTemplateMapper;
import com.diybracelet.mall.customer.infrastructure.CustomerCouponMapper;
import com.diybracelet.mall.identity.domain.CustomerIdentity;
import com.diybracelet.mall.identity.domain.CustomerIdentityResolver;
import com.diybracelet.mall.identity.infrastructure.CustomerIdentityMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CouponService {
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";
    private static final String UNUSED = "UNUSED";
    private static final String EXPIRED = "EXPIRED";
    private static final String RECIPIENT_SELECTED = "SELECTED";
    private static final String RECIPIENT_ALL = "ALL";
    private static final int INSERT_BATCH_SIZE = 500;

    private final CustomerIdentityResolver identityResolver;
    private final CustomerIdentityMapper identityMapper;
    private final CouponTemplateMapper templateMapper;
    private final CustomerCouponMapper customerCouponMapper;

    public CouponService(
            CustomerIdentityResolver identityResolver,
            CustomerIdentityMapper identityMapper,
            CouponTemplateMapper templateMapper,
            CustomerCouponMapper customerCouponMapper
    ) {
        this.identityResolver = identityResolver;
        this.identityMapper = identityMapper;
        this.templateMapper = templateMapper;
        this.customerCouponMapper = customerCouponMapper;
    }

    public List<CouponTemplateView> listTemplates() {
        return templateMapper.selectList(
                new LambdaQueryWrapper<CouponTemplate>()
                        .orderByDesc(CouponTemplate::getCreatedAt)
        ).stream().map(this::templateView).toList();
    }

    @Transactional
    public CouponTemplateView createTemplate(SaveCouponTemplateRequest request) {
        CouponTemplate template = new CouponTemplate();
        applyTemplate(template, request);
        try {
            templateMapper.insert(template);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "优惠券编码已存在"
            );
        }
        return templateView(templateMapper.selectById(template.getId()));
    }

    @Transactional
    public CouponTemplateView updateTemplate(
            Long templateId,
            SaveCouponTemplateRequest request
    ) {
        CouponTemplate template = requireTemplate(templateId);
        applyTemplate(template, request);
        try {
            templateMapper.updateById(template);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "优惠券编码已存在"
            );
        }
        return templateView(templateMapper.selectById(templateId));
    }

    @Transactional
    public CustomerCouponView grant(Long templateId, GrantCouponRequest request) {
        CouponTemplate template = requireTemplate(templateId);
        if (!ACTIVE.equals(template.getStatus())) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "优惠券已停用，不能发送"
            );
        }
        CustomerIdentity identity = identityMapper.selectById(
                request.customerIdentityId()
        );
        if (identity == null || !ACTIVE.equals(identity.getStatus())) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "顾客不存在或已停用"
            );
        }
        LocalDateTime issuedAt = LocalDateTime.now();
        CustomerCoupon coupon = new CustomerCoupon();
        coupon.setCouponTemplateId(templateId);
        coupon.setCustomerIdentityId(identity.getId());
        coupon.setStatus(UNUSED);
        coupon.setIssuedAt(issuedAt);
        coupon.setExpiresAt(issuedAt.plusDays(template.getValidDays()));
        coupon.setNote(trimToNull(request.note()));
        customerCouponMapper.insert(coupon);
        return couponView(coupon, template);
    }

    @Transactional
    public BatchGrantCouponResult grantBatch(
            Long templateId,
            BatchGrantCouponRequest request
    ) {
        CouponTemplate template = requireTemplate(templateId);
        if (!ACTIVE.equals(template.getStatus())) {
            throw new BusinessException(
                    ErrorCode.COMMON_CONFLICT,
                    "优惠券已停用，不能发送"
            );
        }
        String mode = request.recipientMode().trim().toUpperCase();
        if (!RECIPIENT_SELECTED.equals(mode) && !RECIPIENT_ALL.equals(mode)) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "发送范围只能是 SELECTED 或 ALL"
            );
        }

        List<CustomerIdentity> recipients;
        int requestedCount;
        if (RECIPIENT_ALL.equals(mode)) {
            recipients = identityMapper.selectList(
                    new LambdaQueryWrapper<CustomerIdentity>()
                            .eq(CustomerIdentity::getStatus, ACTIVE)
                            .orderByAsc(CustomerIdentity::getId)
            );
            requestedCount = recipients.size();
        } else {
            Set<Long> requestedIds = new LinkedHashSet<>(
                    request.customerIdentityIds() == null
                            ? List.of()
                            : request.customerIdentityIds()
            );
            if (requestedIds.isEmpty()) {
                throw new BusinessException(
                        ErrorCode.COMMON_BAD_REQUEST,
                        "请至少选择一位顾客"
                );
            }
            requestedCount = requestedIds.size();
            Map<Long, CustomerIdentity> identitiesById = identityMapper
                    .selectBatchIds(requestedIds)
                    .stream()
                    .filter(identity -> ACTIVE.equals(identity.getStatus()))
                    .collect(Collectors.toMap(
                            CustomerIdentity::getId,
                            Function.identity()
                    ));
            recipients = requestedIds.stream()
                    .map(identitiesById::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        if (!recipients.isEmpty()) {
            LocalDateTime issuedAt = LocalDateTime.now();
            String note = trimToNull(request.note());
            List<CustomerCoupon> grants = new ArrayList<>(recipients.size());
            for (CustomerIdentity identity : recipients) {
                CustomerCoupon coupon = new CustomerCoupon();
                coupon.setCouponTemplateId(templateId);
                coupon.setCustomerIdentityId(identity.getId());
                coupon.setStatus(UNUSED);
                coupon.setIssuedAt(issuedAt);
                coupon.setExpiresAt(issuedAt.plusDays(template.getValidDays()));
                coupon.setNote(note);
                grants.add(coupon);
            }
            for (int start = 0; start < grants.size(); start += INSERT_BATCH_SIZE) {
                int end = Math.min(start + INSERT_BATCH_SIZE, grants.size());
                customerCouponMapper.insertBatch(grants.subList(start, end));
            }
        }
        int grantedCount = recipients.size();
        return new BatchGrantCouponResult(
                requestedCount,
                grantedCount,
                requestedCount - grantedCount
        );
    }

    @Transactional
    public List<CustomerCouponView> listMine() {
        Long customerId = identityResolver.requireCustomerIdentityId();
        List<CustomerCoupon> coupons = customerCouponMapper.selectList(
                new LambdaQueryWrapper<CustomerCoupon>()
                        .eq(CustomerCoupon::getCustomerIdentityId, customerId)
                        .orderByDesc(CustomerCoupon::getIssuedAt)
        );
        if (coupons.isEmpty()) return List.of();
        LocalDateTime now = LocalDateTime.now();
        for (CustomerCoupon coupon : coupons) {
            if (UNUSED.equals(coupon.getStatus())
                    && coupon.getExpiresAt().isBefore(now)) {
                coupon.setStatus(EXPIRED);
                customerCouponMapper.updateById(coupon);
            }
        }
        Map<Long, CouponTemplate> templates = templateMapper.selectBatchIds(
                coupons.stream().map(CustomerCoupon::getCouponTemplateId).distinct().toList()
        ).stream().collect(Collectors.toMap(
                CouponTemplate::getId,
                Function.identity()
        ));
        return coupons.stream()
                .filter(coupon -> templates.containsKey(coupon.getCouponTemplateId()))
                .map(coupon -> couponView(
                        coupon,
                        templates.get(coupon.getCouponTemplateId())
                ))
                .toList();
    }

    private CouponTemplate requireTemplate(Long templateId) {
        CouponTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "优惠券模板不存在"
            );
        }
        return template;
    }

    private void applyTemplate(
            CouponTemplate template,
            SaveCouponTemplateRequest request
    ) {
        String status = request.status().trim().toUpperCase();
        if (!ACTIVE.equals(status) && !INACTIVE.equals(status)) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "优惠券状态只能是 ACTIVE 或 INACTIVE"
            );
        }
        template.setCode(request.code().trim().toUpperCase());
        template.setName(request.name().trim());
        template.setDescription(request.description().trim());
        template.setDiscountAmount(request.discountAmount());
        template.setMinimumSpend(request.minimumSpend());
        template.setValidDays(request.validDays());
        template.setStatus(status);
    }

    private CouponTemplateView templateView(CouponTemplate template) {
        long issuedCount = customerCouponMapper.selectCount(
                new LambdaQueryWrapper<CustomerCoupon>()
                        .eq(CustomerCoupon::getCouponTemplateId, template.getId())
        );
        return new CouponTemplateView(
                template.getId(), template.getCode(), template.getName(),
                template.getDescription(), template.getDiscountAmount(),
                template.getMinimumSpend(), template.getValidDays(),
                template.getStatus(), issuedCount, template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    private CustomerCouponView couponView(
            CustomerCoupon coupon,
            CouponTemplate template
    ) {
        return new CustomerCouponView(
                coupon.getId(), template.getId(), template.getCode(),
                template.getName(), template.getDescription(),
                template.getDiscountAmount(), template.getMinimumSpend(),
                coupon.getStatus(), coupon.getIssuedAt(), coupon.getExpiresAt(),
                coupon.getNote()
        );
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
