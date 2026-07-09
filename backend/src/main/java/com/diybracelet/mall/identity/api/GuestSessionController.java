package com.diybracelet.mall.identity.api;

import com.diybracelet.mall.identity.api.dto.GuestSessionRequest;
import com.diybracelet.mall.identity.api.dto.GuestSessionResponse;
import com.diybracelet.mall.identity.application.GuestIdentityService;
import com.diybracelet.mall.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 小程序访客会话接口。
 *
 * <p>当前产品阶段暂不强制微信登录，小程序先用安装级 UUID 换取顾客 JWT，
 * 后续微信授权接入后仍可复用同一套顾客侧接口权限模型。</p>
 */
@RestController
@RequestMapping("/api/app/session")
public class GuestSessionController {

    private final GuestIdentityService guestIdentityService;

    public GuestSessionController(GuestIdentityService guestIdentityService) {
        this.guestIdentityService = guestIdentityService;
    }

    /**
     * 建立或复用匿名访客身份。
     *
     * @param request 访客安装身份请求
     * @return 顾客 JWT 与内部身份主键
     */
    @PostMapping("/guest")
    public ApiResponse<GuestSessionResponse> guest(
            @Valid @RequestBody GuestSessionRequest request
    ) {
        return ApiResponse.success(
                guestIdentityService.exchange(request.installationId()),
                UUID.randomUUID().toString()
        );
    }
}
