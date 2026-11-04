package com.filehome.share;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filehome.share.ShareDtos.CreateShareRequest;
import com.filehome.share.ShareDtos.ShareView;

@RestController
@RequestMapping("/api/v1/shares")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    public ShareView create(
            @RequestAttribute("auth.userId") UUID userId,
            @RequestBody CreateShareRequest request) {
        return shareService.create(userId, request);
    }

    @GetMapping
    public List<ShareView> list(@RequestAttribute("auth.userId") UUID userId) {
        return shareService.list(userId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id) {
        shareService.revoke(userId, id);
        return ResponseEntity.noContent().build();
    }
}
