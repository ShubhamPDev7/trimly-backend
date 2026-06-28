package com.trimly.backend.controller;

import com.trimly.backend.dto.s3.PresignedUrlResponse;
import com.trimly.backend.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.region}")
    private String region;

    @GetMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
            @RequestParam String folder,
            @RequestParam String filename,
            @RequestParam(defaultValue = "image/jpeg") String contentType
    ) {
        String key = folder + "/" + UUID.randomUUID() + "-" + filename;

        String uploadUrl = s3Service.generatePresignedUploadUrl(folder, filename, contentType);
        String publicUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;

        return ResponseEntity.ok(new PresignedUrlResponse(uploadUrl, publicUrl, key));
    }
}