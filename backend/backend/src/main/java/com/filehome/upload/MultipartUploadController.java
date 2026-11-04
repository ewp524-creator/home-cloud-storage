package com.filehome.upload;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filehome.upload.MultipartUploadDtos.CompleteUploadResponse;
import com.filehome.upload.MultipartUploadDtos.InitiateUploadRequest;
import com.filehome.upload.MultipartUploadDtos.UploadPartResult;
import com.filehome.upload.MultipartUploadDtos.UploadSessionList;
import com.filehome.upload.MultipartUploadDtos.UploadSessionView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/uploads")
public class MultipartUploadController {

    private final MultipartUploadService uploadService;

    public MultipartUploadController(MultipartUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    public UploadSessionView initiate(
            @RequestAttribute("auth.userId") UUID userId,
            @Valid @RequestBody InitiateUploadRequest request) {
        return uploadService.initiate(userId, request);
    }

    @GetMapping
    public UploadSessionList list(
            @RequestAttribute("auth.userId") UUID userId) {
        return uploadService.listActive(userId);
    }

    @GetMapping("/{id}")
    public UploadSessionView get(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id) {
        return uploadService.get(userId, id);
    }

    @PutMapping(
            value = "/{id}/parts/{partNumber}",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public UploadPartResult uploadPart(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id,
            @PathVariable int partNumber,
            @RequestHeader("X-Chunk-SHA256") String sha256,
            HttpServletRequest request) throws IOException {
        return uploadService.uploadPart(
                userId,
                id,
                partNumber,
                request.getContentLengthLong(),
                sha256,
                request.getInputStream());
    }

    @PostMapping("/{id}/complete")
    public CompleteUploadResponse complete(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id) {
        return uploadService.complete(userId, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> abort(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id) {
        uploadService.abort(userId, id);
        return ResponseEntity.noContent().build();
    }
}
