package com.filehome.share;

import java.time.Instant;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filehome.auth.PasswordHasher;
import com.filehome.common.ApiException;
import com.filehome.config.AppProperties;
import com.filehome.file.FileEntry;
import com.filehome.file.FileMetadataCache;
import com.filehome.file.FileService;
import com.filehome.file.FileView;
import com.filehome.share.ShareDtos.CreateShareRequest;
import com.filehome.share.ShareDtos.PublicShareView;
import com.filehome.share.ShareDtos.ShareView;
import com.filehome.user.UserAccount;
import com.filehome.user.UserService;
import com.filehome.user.UserView;

@Service
public class ShareService {

    private final ShareRepository shareRepository;
    private final ShareTokenService tokenService;
    private final FileService fileService;
    private final UserService userService;
    private final PasswordHasher passwordHasher;
    private final ShareAccessCodeCipher accessCodeCipher;
    private final FileMetadataCache metadataCache;
    private final AppProperties properties;

    public ShareService(
            ShareRepository shareRepository,
            ShareTokenService tokenService,
            FileService fileService,
            UserService userService,
            PasswordHasher passwordHasher,
            ShareAccessCodeCipher accessCodeCipher,
            FileMetadataCache metadataCache,
            AppProperties properties) {
        this.shareRepository = shareRepository;
        this.tokenService = tokenService;
        this.fileService = fileService;
        this.userService = userService;
        this.passwordHasher = passwordHasher;
        this.accessCodeCipher = accessCodeCipher;
        this.metadataCache = metadataCache;
        this.properties = properties;
    }

    @Transactional
    public ShareView create(UUID ownerId, CreateShareRequest request) {
        if (request.rootEntryId() == null) {
            throw ApiException.badRequest("ROOT_REQUIRED", "请选择要分享的文件或文件夹");
        }
        UserAccount owner = userService.require(ownerId);
        FileEntry root = fileService.requireActive(ownerId, request.rootEntryId());
        int days = Math.min(Math.max(request.days() <= 0 ? 7 : request.days(), 1), 30);
        String permission = "view".equalsIgnoreCase(request.permission())
                || "preview".equalsIgnoreCase(request.permission())
                ? "VIEW_ONLY"
                : "DOWNLOAD";
        String code = normalizeCode(request.accessCode());
        if (request.accessCode() != null
                && !request.accessCode().isBlank()
                && (code == null || code.length() < 4 || code.length() > 12)) {
            throw ApiException.badRequest(
                    "INVALID_ACCESS_CODE",
                    "提取码需要包含 4-12 位字母或数字");
        }
        ShareLink existing = shareRepository
                .findByOwnerAndRoot(ownerId, root.id())
                .orElse(null);
        UUID id = existing == null ? UUID.randomUUID() : existing.id();
        String token = tokenService.tokenFor(id);
        Instant now = Instant.now();
        ShareLink share = new ShareLink(
                id,
                ownerId,
                root.id(),
                PasswordHasher.sha256(token),
                code == null ? null : accessCodeHash(id, code),
                code == null ? null : accessCodeCipher.encrypt(code),
                permission,
                root.isFolder() && request.recursive(),
                true,
                now.plusSeconds(days * 24L * 60L * 60L),
                null,
                existing == null ? now : existing.createdAt());
        if (existing == null) {
            shareRepository.insert(share);
        } else {
            shareRepository.updateShare(share);
        }
        metadataCache.invalidateAfterCommit(ownerId);
        return view(share, owner, root, token, code);
    }

    public List<ShareView> list(UUID ownerId) {
        UserAccount owner = userService.require(ownerId);
        List<ShareLink> shares = shareRepository.findAllByOwner(ownerId);
        if (shares.isEmpty()) {
            return List.of();
        }
        Map<UUID, FileEntry> roots = fileService.activeEntries(
                ownerId,
                shares.stream().map(ShareLink::rootEntryId).toList());
        Map<UUID, Long> childCounts = fileService.childCounts(
                ownerId,
                roots.values().stream()
                        .filter(FileEntry::isFolder)
                        .map(FileEntry::id)
                        .toList());
        Map<UUID, ShareAccessCount> accessCounts = shareRepository
                .findAccessCounts(ownerId)
                .stream()
                .collect(Collectors.toMap(
                        ShareAccessCount::shareId,
                        Function.identity()));
        return shares.stream()
                .filter(share -> roots.containsKey(share.rootEntryId()))
                .map(share -> {
                    FileEntry root = roots.get(share.rootEntryId());
                    ShareAccessCount access = accessCounts.get(share.id());
                    return view(
                            share,
                            owner,
                            root,
                            tokenService.tokenFor(share.id()),
                            accessCodeCipher.decrypt(
                                    share.accessCodeEncrypted()),
                            childCounts.getOrDefault(root.id(), 0L),
                            access == null ? 0 : access.visits(),
                            access == null ? 0 : access.downloads());
                })
                .toList();
    }

    @Transactional
    public void revoke(UUID ownerId, UUID shareId) {
        shareRepository.findById(ownerId, shareId)
                .orElseThrow(() -> ApiException.notFound("分享不存在"));
        shareRepository.revoke(ownerId, shareId);
        metadataCache.invalidateAfterCommit(ownerId);
    }

    public PublicShareView publicView(String token) {
        ShareLink share = requireToken(token);
        return publicView(share);
    }

    @Transactional
    public boolean verify(String token, String accessCode) {
        ShareLink share = requireToken(token);
        boolean valid = verifyCode(share, accessCode);
        shareRepository.log(share.id(), null, "VERIFY_CODE", valid);
        return valid;
    }

    public ShareLink requireDownload(String token, String accessCode, UUID viewerUserId) {
        ShareLink share = requireToken(token);
        if (!"DOWNLOAD".equals(share.permission())) {
            throw ApiException.forbidden("该分享仅允许在线预览");
        }
        if (share.downloadRequiresAuth() && viewerUserId == null) {
            throw ApiException.unauthorized("下载前请先登录或注册");
        }
        if (share.accessCodeHash() != null && !verifyCode(share, accessCode)) {
            shareRepository.log(share.id(), viewerUserId, "DOWNLOAD", false);
            throw ApiException.forbidden("提取码不正确");
        }
        return share;
    }

    public ShareLink requirePreview(String token, String accessCode) {
        ShareLink share = requireToken(token);
        if (share.accessCodeHash() != null && !verifyCode(share, accessCode)) {
            shareRepository.log(share.id(), null, "PREVIEW", false);
            throw ApiException.forbidden("提取码不正确");
        }
        shareRepository.log(share.id(), null, "PREVIEW", true);
        return share;
    }

    public void recordDownload(ShareLink share, UUID viewerUserId) {
        shareRepository.log(share.id(), viewerUserId, "DOWNLOAD", true);
    }

    public FileEntry rootEntry(ShareLink share) {
        return fileService.requireActive(share.ownerId(), share.rootEntryId());
    }

    private ShareLink requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.notFound("分享链接无效");
        }
        ShareLink share = shareRepository.findByTokenHash(
                PasswordHasher.sha256(token))
                .orElseThrow(() -> ApiException.notFound("分享链接不存在"));
        if (!share.active()) {
            throw ApiException.notFound("分享链接已失效");
        }
        shareRepository.log(share.id(), null, "VIEW", true);
        return share;
    }

    private PublicShareView publicView(ShareLink share) {
        UserAccount owner = userService.require(share.ownerId());
        FileEntry root = fileService.requireActive(share.ownerId(), share.rootEntryId());
        FileView rootView = fileService.details(share.ownerId(), root.id());
        List<FileView> folderItems = root.isFolder()
                ? fileService.list(
                        share.ownerId(),
                        root.id(),
                        null,
                        "all",
                        null,
                        "modified-desc").items()
                : List.of();
        return new PublicShareView(
                tokenService.tokenFor(share.id()),
                root.name(),
                owner.displayName(),
                UserView.from(owner).initials(),
                root.isFolder() ? "folder" : "file",
                "DOWNLOAD".equals(share.permission()) ? "download" : "preview",
                "active",
                share.createdAt(),
                share.expiresAt(),
                share.accessCodeHash() != null,
                share.downloadRequiresAuth(),
                rootView,
                folderItems);
    }

    private ShareView view(
            ShareLink share,
            UserAccount owner,
            FileEntry root,
            String token,
            String accessCode) {
        return view(
                share,
                owner,
                root,
                token,
                accessCode,
                root.isFolder() ? fileService.childCount(owner.id(), root.id()) : 0,
                0,
                0);
    }

    private ShareView view(
            ShareLink share,
            UserAccount owner,
            FileEntry root,
            String token,
            String accessCode,
            long itemCount,
            long visits,
            long downloads) {
        return new ShareView(
                share.id(),
                root.id(),
                root.name(),
                owner.displayName(),
                UserView.from(owner).initials(),
                root.isFolder() ? "folder" : "file",
                List.of(root.id()),
                itemCount,
                share.createdAt(),
                share.expiresAt(),
                accessCode,
                share.accessCodeHash() != null,
                visits,
                downloads,
                "DOWNLOAD".equals(share.permission()) ? "download" : "preview",
                share.active() ? "active" : "expired",
                token,
                properties.share().baseUrl() + "/s/" + token);
    }

    private String accessCodeHash(UUID shareId, String code) {
        return passwordHasher.hash(shareId + ":" + code);
    }

    private boolean verifyCode(ShareLink share, String rawCode) {
        if (share.accessCodeHash() == null) return true;
        String code = normalizeCode(rawCode);
        if (code == null) return false;
        if (share.accessCodeHash().startsWith("pbkdf2$")) {
            return passwordHasher.matches(
                    share.id() + ":" + code,
                    share.accessCodeHash());
        }
        byte[] expected;
        byte[] actual;
        try {
            expected = Base64.getDecoder().decode(share.accessCodeHash());
            actual = PasswordHasher.sha256(share.id() + ":" + code);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) return null;
        String value = code.trim();
        if (value.length() > 64) return null;
        return value.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
