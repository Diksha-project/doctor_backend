package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Stores private medical-report objects in Cloudflare R2 through its S3-compatible API. */
@Service
public class CloudflareR2StorageService {
    public record StoredFile(String objectKey, String contentType, String fileName, long size) {}
    public record FileContent(byte[] bytes, String contentType, String fileName) {}

    @Value("${cloudflare.r2.account-id:}") private String accountId;
    @Value("${cloudflare.r2.bucket:}") private String bucket;
    @Value("${cloudflare.r2.access-key-id:}") private String accessKeyId;
    @Value("${cloudflare.r2.secret-access-key:}") private String secretAccessKey;

    private final AtomicReference<S3Client> client = new AtomicReference<>();

    public boolean isConfigured() {
        return hasValue(accountId) && hasValue(bucket) && hasValue(accessKeyId) && hasValue(secretAccessKey);
    }

    public StoredFile storeMedicalReport(Long organizationId, Long patientId, Long doctorId,
                                         byte[] bytes, String contentType, String originalFileName) {
        if (!isConfigured()) throw new IllegalStateException("Cloudflare R2 storage is not configured");
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Medical report is empty");
        String extension = extensionFor(contentType);
        String objectKey = "organizations/" + organizationId + "/patients/" + patientId
                + "/doctors/" + doctorId + "/medical-reports/" + UUID.randomUUID() + extension;
        String safeName = sanitizeFileName(originalFileName, extension);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .metadata(Map.of("document-kind", "medical-report", "organization-id", organizationId.toString(),
                        "patient-id", patientId.toString()))
                .build();
        client().putObject(request, RequestBody.fromBytes(bytes));
        return new StoredFile(objectKey, contentType, safeName, bytes.length);
    }

    public FileContent read(String objectKey, String contentType, String fileName) {
        if (!isConfigured()) throw new IllegalStateException("Cloudflare R2 storage is not configured");
        byte[] bytes = client().getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket).key(objectKey).build()).asByteArray();
        return new FileContent(bytes, contentType, fileName);
    }

    private S3Client client() {
        S3Client current = client.get();
        if (current != null) return current;
        synchronized (client) {
            current = client.get();
            if (current == null) {
                S3Client created = S3Client.builder()
                        .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                        .region(Region.of("auto"))
                        .serviceConfiguration(S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .chunkedEncodingEnabled(false)
                                .build())
                        .build();
                client.set(created);
                current = created;
            }
        }
        return current;
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Only PDF, JPEG, PNG, and WebP medical reports are supported");
        };
    }

    private String sanitizeFileName(String fileName, String extension) {
        String value = fileName == null || fileName.isBlank() ? "medical-report" + extension : fileName;
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.length() > 200) value = value.substring(value.length() - 200);
        return value;
    }

    private boolean hasValue(String value) { return value != null && !value.isBlank(); }

    @PreDestroy
    public void close() {
        S3Client current = client.getAndSet(null);
        if (current != null) current.close();
    }
}
