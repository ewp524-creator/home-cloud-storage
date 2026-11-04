package com.filehome.share;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.filehome.common.ApiException;
import com.filehome.file.SharedPreviewAuthorizer;

@Component
public class SharePreviewAuthorizer implements SharedPreviewAuthorizer {

    private final ShareRepository shareRepository;

    public SharePreviewAuthorizer(ShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    @Override
    public void requireActive(UUID ownerId, UUID shareId) {
        ShareLink share = shareRepository.findById(ownerId, shareId)
                .filter(ShareLink::active)
                .filter(item -> item.expiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.forbidden("分享链接已失效"));
    }
}
