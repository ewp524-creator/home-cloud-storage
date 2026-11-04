package com.filehome.share;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.filehome.auth.RequestAuthenticator;
import com.filehome.common.ApiException;
import com.filehome.file.FileEntry;
import com.filehome.file.FilePreviewService;
import com.filehome.file.FilePreviewService.PreviewAccess;
import com.filehome.file.FileService;
import com.filehome.security.ClientAddressResolver;
import com.filehome.security.RequestRateLimiter;
import com.filehome.share.ShareDtos.PublicShareView;
import com.filehome.share.ShareDtos.VerifyRequest;
import com.filehome.share.ShareDtos.VerifyResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/public/shares")
public class PublicShareController {

    private final ShareService shareService;
    private final FileService fileService;
    private final FilePreviewService filePreviewService;
    private final RequestAuthenticator requestAuthenticator;
    private final RequestRateLimiter rateLimiter;
    private final ClientAddressResolver addressResolver;

    public PublicShareController(
            ShareService shareService,
            FileService fileService,
            FilePreviewService filePreviewService,
            RequestAuthenticator requestAuthenticator,
            RequestRateLimiter rateLimiter,
            ClientAddressResolver addressResolver) {
        this.shareService = shareService;
        this.fileService = fileService;
        this.filePreviewService = filePreviewService;
        this.requestAuthenticator = requestAuthenticator;
        this.rateLimiter = rateLimiter;
        this.addressResolver = addressResolver;
    }

    @GetMapping("/{token}")
    public PublicShareView metadata(
            @PathVariable String token,
            HttpServletRequest request) {
        rateLimiter.check(
                "share-metadata:" + addressResolver.resolve(request),
                120,
                Duration.ofMinutes(1));
        return shareService.publicView(token);
    }

    @PostMapping("/{token}/verify")
    public VerifyResponse verify(
            @PathVariable String token,
            @Valid @RequestBody VerifyRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check(
                "share-verify:" + addressResolver.resolve(servletRequest) + ":" + token,
                10,
                Duration.ofMinutes(1));
        return new VerifyResponse(shareService.verify(token, request.accessCode()));
    }

    @PostMapping("/{token}/preview-access")
    public PreviewAccess previewAccess(
            @PathVariable String token,
            @Valid @RequestBody VerifyRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check(
                "share-preview:" + addressResolver.resolve(servletRequest) + ":" + token,
                60,
                Duration.ofMinutes(1));
        ShareLink share = shareService.requirePreview(token, request.accessCode());
        FileEntry root = shareService.rootEntry(share);
        return filePreviewService.sharedAccess(share.id(), root);
    }

    @GetMapping("/{token}/download")
    public ResponseEntity<?> download(
            @PathVariable String token,
            @RequestParam(required = false) String accessCode,
            HttpServletRequest request) {
        rateLimiter.check(
                "share-download:" + addressResolver.resolve(request) + ":" + token,
                30,
                Duration.ofMinutes(1));
        UUID viewerId = requestAuthenticator.resolve(request)
                .orElseThrow(() -> ApiException.unauthorized(
                        "\u4e0b\u8f7d\u524d\u8bf7\u5148\u767b\u5f55\u6216\u6ce8\u518c"))
                .user()
                .id();
        ShareLink share = shareService.requireDownload(token, accessCode, viewerId);
        FileEntry root = shareService.rootEntry(share);
        shareService.recordDownload(share, viewerId);
        if (!root.isFolder()) {
            InputStream input = fileService.openObject(root);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, attachment(root.name()))
                    .contentType(mediaType(filePreviewService.contentType(root)))
                    .contentLength(root.size())
                    .body(new InputStreamResource(input));
        }
        StreamingResponseBody body = outputStream ->
                fileService.writeFolderZip(share.ownerId(), root.id(), outputStream);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        attachment(root.name() + ".zip"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    private String attachment(String name) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ContentDisposition.attachment()
                .filename(name, StandardCharsets.UTF_8)
                .build()
                .toString() + "; filename*=UTF-8''" + encoded;
    }

    private MediaType mediaType(String mimeType) {
        try {
            return mimeType == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(mimeType);
        } catch (RuntimeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
