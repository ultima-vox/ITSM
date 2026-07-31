package ru.ultimavox.itsm.platform.storage.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.storage.Attachment;
import ru.ultimavox.itsm.platform.storage.AttachmentService;

@RestController
@RequestMapping("/api/v1/attachments")
@Tag(name = "Platform — Attachments")
class AttachmentController {

  private final AttachmentService attachments;
  private final AccessControl access;

  AttachmentController(AttachmentService attachments, AccessControl access) {
    this.attachments = attachments;
    this.access = access;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload an attachment")
  ResponseEntity<AttachmentResponse> upload(
      Authentication authentication,
      @RequestPart("file") MultipartFile file
  ) throws IOException {
    String actor = authentication.getName();
    access.require(actor, "attachment.write", "attachment", null);

    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
    }

    String filename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
        ? "file"
        : file.getOriginalFilename();

    try (InputStream content = file.getInputStream()) {
      Attachment saved = attachments.upload(
          actor,
          filename,
          file.getContentType(),
          file.getSize(),
          content
      );
      AttachmentResponse body = AttachmentResponse.from(saved);
      return ResponseEntity.created(URI.create("/api/v1/attachments/" + saved.id())).body(body);
    }
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get attachment metadata")
  AttachmentResponse metadata(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "attachment.read", "attachment", id.toString());
    return attachments.findById(id)
        .map(AttachmentResponse::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
  }

  @GetMapping("/{id}/content")
  @Operation(summary = "Download attachment content (when storage backend holds bytes)")
  ResponseEntity<InputStreamResource> content(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "attachment.read", "attachment", id.toString());
    Attachment attachment = attachments.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));

    if (!attachment.isDownloadAllowed()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Download blocked: scan status is " + attachment.scanStatus()
      );
    }

    InputStream stream = attachments.openContent(attachment)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "Content streaming not available for current storage backend"
        ));

    MediaType mediaType;
    try {
      mediaType = MediaType.parseMediaType(attachment.contentType());
    } catch (Exception ex) {
      mediaType = MediaType.APPLICATION_OCTET_STREAM;
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(mediaType);
    headers.setContentLength(attachment.sizeBytes());
    headers.set(
        HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + attachment.filename().replace("\"", "") + "\""
    );

    return new ResponseEntity<>(new InputStreamResource(stream), headers, HttpStatus.OK);
  }

  record AttachmentResponse(
      UUID id,
      String filename,
      String contentType,
      long size,
      String objectKey,
      String scanStatus,
      String scanEngine,
      String scanDetail
  ) {
    static AttachmentResponse from(Attachment a) {
      return new AttachmentResponse(
          a.id(),
          a.filename(),
          a.contentType(),
          a.sizeBytes(),
          a.storageKey(),
          a.scanStatus() == null ? "PENDING" : a.scanStatus().name(),
          a.scanEngine(),
          a.scanDetail()
      );
    }
  }
}
