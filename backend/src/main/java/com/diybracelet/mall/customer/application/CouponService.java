package com.diybracelet.mall.customer.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.BatchGrantCouponRequest;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.BatchGrantCouponResult;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.CouponTemplateView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.CustomerCouponView;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.GrantCouponRequest;
import com.diybracelet.mall.customer.api.dto.CustomerDtos.SaveCouponTemplateRequest;
import com.diybracelet.mall.customer.domain.CouponTemplate;
import com.diybracelet.mall.customer.domain.CouponGrantBatch;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;


/** 管理优惠券领取、过期状态和发放规则，所有顾客操作均校验归属。 */
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
        CouponGrantBatch batch = beginBatch(request.batchId(), templateId, "SINGLE",
                List.of(request.customerIdentityId()), request.note());
        if (batch.requestedCount() != null) return couponView(customerCouponMapper.selectById(batch.couponId()));
        CouponTemplate template = requireIssuableTemplate(templateId);
        CustomerIdentity identity = identityMapper.selectById(request.customerIdentityId());
        if (identity == null || !ACTIVE.equals(identity.getStatus())) {
            throw new BusinessException(ErrorCode.COMMON_NOT_FOUND, "顾客不存在或已停用");
        }
        CustomerCoupon coupon = issuedCoupon(template, identity.getId(), request.note(), request.batchId(), LocalDateTime.now());
        customerCouponMapper.insert(coupon);
        customerCouponMapper.completeBatch(request.batchId(), 1, 1, 0, coupon.getId());
        return couponView(coupon);
    }

    @Transactional
    public BatchGrantCouponResult grantBatch(Long templateId, BatchGrantCouponRequest request) {
        String mode = request.recipientMode().trim().toUpperCase(Locale.ROOT);
        if (!RECIPIENT_SELECTED.equals(mode) && !RECIPIENT_ALL.equals(mode)) {
            throw new BusinessException(ErrorCode.COMMON_BAD_REQUEST, "发送范围只能是 SELECTED 或 ALL");
        }
        List<Long> requestedIds = request.customerIdentityIds() == null ? List.of()
                : request.customerIdentityIds().stream().distinct().sorted().toList();
        if (RECIPIENT_SELECTED.equals(mode) && requestedIds.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON_BAD_REQUEST, "请至少选择一位顾客");
        }
        if (RECIPIENT_ALL.equals(mode) && !requestedIds.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON_BAD_REQUEST, "全量发券不能同时指定顾客");
        }
        CouponGrantBatch batch = beginBatch(request.batchId(), templateId, mode, requestedIds, request.note());
        // 先返回原结果：模板停发、全量客户集合变化均不改变已完成批次。
        if (batch.requestedCount() != null) {
            return new BatchGrantCouponResult(batch.requestedCount(), batch.grantedCount(), batch.skippedCount());
        }
        CouponTemplate template = requireIssuableTemplate(templateId);
        List<CustomerIdentity> recipients = RECIPIENT_ALL.equals(mode)
                ? identityMapper.selectList(new LambdaQueryWrapper<CustomerIdentity>()
                        .eq(CustomerIdentity::getStatus, ACTIVE).orderByAsc(CustomerIdentity::getId))
                : identityMapper.selectBatchIds(requestedIds).stream()
                        .filter(identity -> ACTIVE.equals(identity.getStatus())).toList();
        int requestedCount = RECIPIENT_ALL.equals(mode) ? recipients.size() : requestedIds.size();
        LocalDateTime issuedAt = LocalDateTime.now();
        List<CustomerCoupon> grants = recipients.stream().map(identity ->
                issuedCoupon(template, identity.getId(), request.note(), request.batchId(), issuedAt)).toList();
        for (int start = 0; start < grants.size(); start += INSERT_BATCH_SIZE) {
            customerCouponMapper.insertBatch(grants.subList(start, Math.min(start + INSERT_BATCH_SIZE, grants.size())));
        }
        int grantedCount = recipients.size();
        customerCouponMapper.completeBatch(request.batchId(), requestedCount, grantedCount,
                requestedCount - grantedCount, null);
        return new BatchGrantCouponResult(requestedCount, grantedCount, requestedCount - grantedCount);
    }

    private CouponTemplate requireIssuableTemplate(Long templateId) {
        CouponTemplate template = requireTemplate(templateId);
        if (!ACTIVE.equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.COMMON_CONFLICT, "优惠券已停用，不能发送");
        }
        return template;
    }

    private CouponGrantBatch beginBatch(String batchId, Long templateId, String mode, List<Long> ids, String note) {
        if (batchId == null || !batchId.matches("[A-Za-z0-9_-]{16,80}")) {
            throw new BusinessException(ErrorCode.COMMON_BAD_REQUEST, "发券批次格式不正确");
        }
        String canonical = templateId + "|" + mode + "|" + ids + "|" + (trimToNull(note) == null ? "" : note.trim());
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        customerCouponMapper.reserveBatch(batchId, hash);
        CouponGrantBatch batch = customerCouponMapper.lockBatch(batchId);
        if (!hash.equals(batch.requestHash())) {
            throw new BusinessException(ErrorCode.COUPON_BATCH_CONFLICT, "该发券批次已用于不同内容，请先确认原批次结果");
        }
        return batch;
    }

    private CustomerCoupon issuedCoupon(CouponTemplate template, Long customerId, String note, String batchId, LocalDateTime issuedAt) {
        CustomerCoupon coupon = new CustomerCoupon();
        coupon.setCouponTemplateId(template.getId());
        coupon.setCustomerIdentityId(customerId);
        coupon.setCouponCode(template.getCode());
        coupon.setCouponName(template.getName());
        coupon.setCouponDescription(template.getDescription());
        coupon.setDiscountAmount(template.getDiscountAmount());
        coupon.setMinimumSpend(template.getMinimumSpend());
        coupon.setSnapshotSource("ISSUED_SNAPSHOT");
        coupon.setGrantBatchId(batchId);
        coupon.setStatus(UNUSED);
        coupon.setIssuedAt(issuedAt);
        coupon.setExpiresAt(issuedAt.plusDays(template.getValidDays()));
        coupon.setNote(trimToNull(note));
        return coupon;
    }

    @Transactional(readOnly = true)
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
                    && !coupon.getExpiresAt().isAfter(now)) {
                // Expiration is a display projection. Persisting a previously
                // read entity here could overwrite an order's concurrent use.
                coupon.setStatus(EXPIRED);
            }
        }
        return coupons.stream().map(this::couponView).toList();
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

    private CustomerCouponView couponView(CustomerCoupon coupon) {
        return new CustomerCouponView(coupon.getId(), coupon.getCouponTemplateId(), coupon.getCouponCode(),
                coupon.getCouponName(), coupon.getCouponDescription(), coupon.getDiscountAmount(), coupon.getMinimumSpend(),
                coupon.getStatus(), coupon.getIssuedAt(), coupon.getExpiresAt(), coupon.getNote());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
