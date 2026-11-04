package com.filehome.file;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.filehome.file.FileOperationDtos.CopyRequest;
import com.filehome.file.FileOperationDtos.CreateFolderRequest;
import com.filehome.file.FileOperationDtos.IdsRequest;
import com.filehome.file.FileOperationDtos.MoveRequest;
import com.filehome.file.FileOperationDtos.OperationResult;
import com.filehome.file.FileOperationDtos.RenameRequest;
import com.filehome.file.FileOperationDtos.StarRequest;
import com.filehome.file.FileService.FileTreeNode;
import com.filehome.file.FilePreviewService.PreviewAccess;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1")
public class FileController {

    private final FileService fileService;
    private final FilePreviewService filePreviewService;

    public FileController(
            FileService fileService,
            FilePreviewService filePreviewService) {
        this.fileService = fileService;
        this.filePreviewService = filePreviewService;
    }

    @GetMapping("/files")
    public FileListResponse list(
            @RequestAttribute("auth.userId") UUID userId,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false, defaultValue = "all") String type,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false, defaultValue = "modified-desc") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "80") int limit) {
        return fileService.list(
                userId,
                parentId,
                query,
                type,
                tag,
                sort,
                cursor,
                Math.min(Math.max(limit, 1), 200));
    }

    @GetMapping("/files/recent")
    public List<FileView> recent(
            @RequestAttribute("auth.userId") UUID userId,
            @RequestParam(defaultValue = "50") int limit) {
        return fileService.recent(userId, Math.min(Math.max(limit, 1), 200));
    }

    @GetMapping("/files/starred")
    public List<FileView> starred(@RequestAttribute("auth.userId") UUID userId) {
        return fileService.starred(userId);
    }

    @GetMapping("/files/{id}")
    public FileView details(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id) {
        return fileService.details(userId, id);
    }

    @GetMapping("/folders/tree")
    public List<FileTreeNode> tree(
            @RequestAttribute("auth.userId") UUID userId,
            @RequestParam(required = false) UUID parentId) {
        return fileService.tree(userId, parentId);
    }

    @PostMapping("/folders")
    public FileView createFolder(
            @RequestAttribute("auth.userId") UUID userId,
            @RequestBody CreateFolderRequest request) {
        return fileService.createFolder(userId, request);
    }

    @PatchMapping("/files/{id}")
    public FileView rename(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id,
            @RequestBody RenameRequest request) {
        return fileService.rename(userId, id, request);
    }

    @PatchMapping("/files/{id}/star")
    public FileView star(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id,
            @RequestBody StarRequest request) {
        return fileService.star(userId, id, request);
    }

    @PostMapping("/files/move")
    public ResponseEntity<Void> move(
            @RequestAttribute("auth.userId") UUID userId,
            @RequestBody MoveRequest request) {
        fileService.move(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/files/copy")
    public OperationResult copy(
            @RequestAttribute("auth.userId") UUID userId,
            @RequestBody CopyRequest request) {
        return fileService.copy(userId, request);
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileView upload(
            @RequestAttribute("auth.userId") UUID userId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) UUID parentId) {
        return fileService.upload(userId, parentId, file);
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id) {
        FileEntry entry = fileService.requireActive(userId, id);
        if (entry.isFolder()) {
            throw com.filehome.common.ApiException.badRequest(
                    "NOT_A_FILE", "文件夹请使用 ZIP 下载接口");
        }
        InputStream input = fileService.openObject(entry);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(entry.name()))
                .contentType(mediaType(filePreviewService.contentType(entry)))
                .contentLength(entry.size())
                .body(new InputStreamResource(input));
    }

    @GetMapping("/files/{id}/preview-access")
    public PreviewAccess previewAccess(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id) {
        return filePreviewService.personalAccess(userId, id);
    }

    @RequestMapping(
            value = "/media/{id}",
            method = { RequestMethod.GET, RequestMethod.HEAD })
    public ResponseEntity<StreamingResponseBody> media(
            @PathVariable UUID id,
            @RequestParam String ticket,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            HttpServletRequest request) {
        FileEntry entry = filePreviewService.requireTicket(ticket, id);
        String previewKind = filePreviewService.previewKind(entry);
        String contentType = FilePreviewType.isTextPreview(previewKind)
                ? "text/plain;charset=UTF-8"
                : filePreviewService.contentType(entry);
        ByteRangeParser.Result selection = ByteRangeParser.parse(range, entry.size());
        if (!selection.valid()) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + entry.size())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .build();
        }

        long length = selection.length();
        HttpStatus status = selection.partial()
                ? HttpStatus.PARTIAL_CONTENT
                : HttpStatus.OK;
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .header(HttpHeaders.CONTENT_DISPOSITION, inline(entry.name()))
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .contentType(mediaType(contentType))
                .contentLength(length);
        if (selection.partial()) {
            builder.header(
                    HttpHeaders.CONTENT_RANGE,
                    "bytes " + selection.start() + "-" + selection.end()
                            + "/" + entry.size());
        }
        if (RequestMethod.HEAD.name().equalsIgnoreCase(request.getMethod())
                || length == 0) {
            return builder.build();
        }
        StreamingResponseBody body = outputStream -> {
            try (InputStream input = filePreviewService.openRange(
                    entry,
                    selection.start(),
                    length)) {
                input.transferTo(outputStream);
            }
        };
        return builder.body(body);
    }

    @GetMapping("/folders/{id}/download")
    public ResponseEntity<StreamingResponseBody> downloadFolder(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id) {
        FileEntry folder = fileService.requireActive(userId, id);
        StreamingResponseBody body = outputStream ->
                fileService.writeFolderZip(userId, folder.id(), outputStream);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(folder.name() + ".zip"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @PostMapping("/trash")
    public ResponseEntity<Void> trash(
            @RequestAttribute("auth.userId") UUID userId,
            @RequestBody IdsRequest request) {
        fileService.trash(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trash")
    public List<FileView> trashList(@RequestAttribute("auth.userId") UUID userId) {
        return fileService.trashList(userId);
    }

    @PostMapping("/trash/restore")
    public ResponseEntity<Void> restore(
            @RequestAttribute("auth.userId") UUID userId,
            @RequestBody IdsRequest request) {
        fileService.restore(userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/trash/{id}")
    public ResponseEntity<Void> deleteTrash(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id) {
        fileService.permanentDelete(userId, new IdsRequest(List.of(id)));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/trash")
    public ResponseEntity<Void> emptyTrash(
            @RequestAttribute("auth.userId") UUID userId) {
        List<UUID> ids = fileService.trashList(userId).stream()
                .map(FileView::id)
                .toList();
        fileService.permanentDelete(userId, new IdsRequest(ids));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/files/{id}/permanent")
    public ResponseEntity<Void> deleteFilePermanent(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id) {
        FileEntry entry = fileService.requireActive(userId, id);
        if (entry.isFolder()) {
            throw com.filehome.common.ApiException.badRequest(
                    "TYPE_MISMATCH", "delfile 只能删除文件");
        }
        fileService.permanentDelete(userId, new IdsRequest(List.of(id)));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/folders/{id}/permanent")
    public ResponseEntity<Void> deleteFolderPermanent(
            @RequestAttribute("auth.userId") UUID userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean recursive) {
        FileEntry entry = fileService.requireActive(userId, id);
        if (!entry.isFolder()) {
            throw com.filehome.common.ApiException.badRequest(
                    "TYPE_MISMATCH", "deldir 只能删除文件夹");
        }
        if (!recursive) {
            throw com.filehome.common.ApiException.badRequest(
                    "RECURSIVE_REQUIRED", "删除文件夹必须确认递归删除");
        }
        fileService.permanentDelete(userId, new IdsRequest(List.of(id)));
        return ResponseEntity.noContent().build();
    }

    private String attachment(String name) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ContentDisposition.attachment()
                .filename(name, StandardCharsets.UTF_8)
                .build()
                .toString() + "; filename*=UTF-8''" + encoded;
    }

    private String inline(String name) {
        return ContentDisposition.inline()
                .filename(name, StandardCharsets.UTF_8)
                .build()
                .toString();
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
