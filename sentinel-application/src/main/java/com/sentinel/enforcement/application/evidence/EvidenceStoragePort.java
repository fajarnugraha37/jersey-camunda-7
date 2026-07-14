package com.sentinel.enforcement.application.evidence;

import java.io.InputStream;
import java.time.Duration;

public interface EvidenceStoragePort {

  String createPresignedUploadUrl(String bucket, String objectKey, Duration ttl);

  String createPresignedDownloadUrl(String bucket, String objectKey, Duration ttl);

  StoredEvidenceObject statObject(String bucket, String objectKey);

  InputStream getObjectStream(String bucket, String objectKey);

  record StoredEvidenceObject(long sizeBytes, String mediaType) {}
}
