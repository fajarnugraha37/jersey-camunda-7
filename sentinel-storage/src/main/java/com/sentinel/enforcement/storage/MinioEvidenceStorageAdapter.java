package com.sentinel.enforcement.storage;

import com.sentinel.enforcement.application.evidence.EvidenceObjectMissingException;
import com.sentinel.enforcement.application.evidence.EvidenceStoragePort;
import com.sentinel.enforcement.application.evidence.EvidenceStorageUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;

public final class MinioEvidenceStorageAdapter implements EvidenceStoragePort {
  private static final String DEFAULT_MINIO_REGION = "us-east-1";

  private final MinioClient minioClient;
  private final MinioClient presigningMinioClient;

  public MinioEvidenceStorageAdapter(
      String endpoint, String publicEndpoint, String accessKey, String secretKey) {
    this(
        MinioClient.builder()
            .endpoint(requireNonBlank(endpoint, "endpoint"))
            .credentials(
                requireNonBlank(accessKey, "accessKey"), requireNonBlank(secretKey, "secretKey"))
            .build(),
        MinioClient.builder()
            .endpoint(requireNonBlank(publicEndpoint, "publicEndpoint"))
            .region(DEFAULT_MINIO_REGION)
            .credentials(
                requireNonBlank(accessKey, "accessKey"), requireNonBlank(secretKey, "secretKey"))
            .build());
  }

  public MinioEvidenceStorageAdapter(String endpoint, String accessKey, String secretKey) {
    this(endpoint, endpoint, accessKey, secretKey);
  }

  MinioEvidenceStorageAdapter(MinioClient minioClient, MinioClient presigningMinioClient) {
    this.minioClient = Objects.requireNonNull(minioClient, "minioClient must not be null");
    this.presigningMinioClient =
        Objects.requireNonNull(
            presigningMinioClient, "presigningMinioClient must not be null");
  }

  public void ensureBucketExists(String bucket) {
    try {
      boolean exists =
          minioClient.bucketExists(BucketExistsArgs.builder().bucket(validBucket(bucket)).build());
      if (!exists) {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      }
    } catch (Exception exception) {
      throw unavailable("Failed to ensure MinIO bucket " + bucket + " exists.", exception);
    }
  }

  @Override
  public String createPresignedUploadUrl(String bucket, String objectKey, Duration ttl) {
    try {
      return presigningMinioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.PUT)
              .bucket(validBucket(bucket))
              .object(validObjectKey(objectKey))
              .expiry(expirySeconds(ttl))
              .build());
    } catch (Exception exception) {
      throw unavailable("Failed to create presigned upload URL.", exception);
    }
  }

  @Override
  public String createPresignedDownloadUrl(String bucket, String objectKey, Duration ttl) {
    try {
      return presigningMinioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(validBucket(bucket))
              .object(validObjectKey(objectKey))
              .expiry(expirySeconds(ttl))
              .build());
    } catch (Exception exception) {
      throw unavailable("Failed to create presigned download URL.", exception);
    }
  }

  @Override
  public StoredEvidenceObject statObject(String bucket, String objectKey) {
    try {
      StatObjectResponse response =
          minioClient.statObject(
              StatObjectArgs.builder()
                  .bucket(validBucket(bucket))
                  .object(validObjectKey(objectKey))
                  .build());
      return new StoredEvidenceObject(
          response.size(), normalizeContentType(response.contentType()));
    } catch (ErrorResponseException exception) {
      if (isMissingObject(exception)) {
        throw new EvidenceObjectMissingException(bucket, objectKey);
      }
      throw unavailable("Failed to stat evidence object " + objectKey + ".", exception);
    } catch (Exception exception) {
      throw unavailable("Failed to stat evidence object " + objectKey + ".", exception);
    }
  }

  @Override
  public InputStream getObjectStream(String bucket, String objectKey) {
    try {
      return minioClient.getObject(
          GetObjectArgs.builder()
              .bucket(validBucket(bucket))
              .object(validObjectKey(objectKey))
              .build());
    } catch (ErrorResponseException exception) {
      if (isMissingObject(exception)) {
        throw new EvidenceObjectMissingException(bucket, objectKey);
      }
      throw unavailable("Failed to open evidence object " + objectKey + ".", exception);
    } catch (Exception exception) {
      throw unavailable("Failed to open evidence object " + objectKey + ".", exception);
    }
  }

  private static boolean isMissingObject(ErrorResponseException exception) {
    String code = exception.errorResponse().code();
    return "NoSuchKey".equals(code) || "NoSuchObject".equals(code);
  }

  private static String normalizeContentType(String contentType) {
    return contentType == null || contentType.isBlank()
        ? "application/octet-stream"
        : contentType.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static int expirySeconds(Duration ttl) {
    Objects.requireNonNull(ttl, "ttl must not be null");
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
    long seconds = ttl.getSeconds();
    if (seconds > 604800) {
      throw new IllegalArgumentException("ttl must not exceed 7 days for MinIO presigned URLs");
    }
    return Math.toIntExact(seconds);
  }

  private static String validBucket(String bucket) {
    return requireNonBlank(bucket, "bucket");
  }

  private static String validObjectKey(String objectKey) {
    return requireNonBlank(objectKey, "objectKey");
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  private static EvidenceStorageUnavailableException unavailable(
      String message, Exception exception) {
    return new EvidenceStorageUnavailableException(message, exception);
  }
}
