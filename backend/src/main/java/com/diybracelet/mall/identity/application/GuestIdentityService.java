package com.diybracelet.mall.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.diybracelet.mall.identity.api.dto.GuestSessionResponse;
import com.diybracelet.mall.identity.domain.CustomerIdentity;
import com.diybracelet.mall.identity.infrastructure.CustomerIdentityMapper;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtService;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 匿名访客身份交换服务。
 *
 * <p>小程序本地安装 UUID 不是访问凭据，不能原样持久化。服务会先用服务端 pepper
 * 生成摘要，再以 {@code GUEST + 摘要} 幂等创建顾客身份，最后签发短小明确的顾客 JWT。</p>
 */
@Service
public class GuestIdentityService {

    private static final String SOURCE_GUEST = "GUEST";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final CustomerIdentityMapper mapper;
    private final CustomerJwtService customerJwtService;
    private final String identityPepper;

    public GuestIdentityService(
            CustomerIdentityMapper mapper,
            CustomerJwtService customerJwtService,
            @Value("${app.customer-auth.identity-pepper}") String identityPepper
    ) {
        if (identityPepper == null
                || identityPepper.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "Customer identity pepper must contain at least 32 bytes"
            );
        }
        this.mapper = mapper;
        this.customerJwtService = customerJwtService;
        this.identityPepper = identityPepper;
    }

    /**
     * 把小程序安装 UUID 交换为系统内部顾客身份和顾客 JWT。
     *
     * <p>唯一键冲突通常只会出现在两个并发请求首次使用同一安装 UUID 时；此时重新查询
     * 已创建记录即可保持接口幂等。</p>
     *
     * @param installationId 小程序安装级 UUID
     * @return 顾客会话信息
     */
    @Transactional
    public GuestSessionResponse exchange(String installationId) {
        String externalKeyHash = sha256Hex(
                installationId.trim().toLowerCase(Locale.ROOT),
                identityPepper
        );
        CustomerIdentity identity = findGuestByHash(externalKeyHash);
        if (identity == null) {
            identity = new CustomerIdentity();
            identity.setSource(SOURCE_GUEST);
            identity.setExternalKeyHash(externalKeyHash);
            identity.setStatus(STATUS_ACTIVE);
            try {
                mapper.insert(identity);
            } catch (DuplicateKeyException exception) {
                identity = findGuestByHash(externalKeyHash);
            }
        }
        if (identity == null || !STATUS_ACTIVE.equals(identity.getStatus())) {
            throw new BusinessException(
                    ErrorCode.COMMON_FORBIDDEN,
                    "访客身份已停用"
            );
        }
        return new GuestSessionResponse(
                identity.getId(),
                customerJwtService.issue(identity.getId()),
                customerJwtService.expiresInSeconds()
        );
    }

    private CustomerIdentity findGuestByHash(String externalKeyHash) {
        return mapper.selectOne(
                new LambdaQueryWrapper<CustomerIdentity>()
                        .eq(CustomerIdentity::getSource, SOURCE_GUEST)
                        .eq(CustomerIdentity::getExternalKeyHash, externalKeyHash)
        );
    }

    private String sha256Hex(String normalizedInstallationId, String pepper) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(
                    (pepper + ":" + normalizedInstallationId)
                            .getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
