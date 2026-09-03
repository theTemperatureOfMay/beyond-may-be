package com.example.beyond_may_be.visit.service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Component
public class VisitPhotoStorage {

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final String bucket;
  private final Duration urlExpiration;

  public VisitPhotoStorage(
      S3Client s3Client,
      S3Presigner s3Presigner,
      @Value("${visit-photo.bucket:}") String bucket,
      @Value("${visit-photo.url-expiration:PT1H}") Duration urlExpiration) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
    this.bucket = bucket;
    this.urlExpiration = urlExpiration;
  }

  public void upload(String objectKey, String contentType, MultipartFile file) {
    PutObjectRequest request =
        PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType(contentType).build();
    try (var inputStream = file.getInputStream()) {
      s3Client.putObject(request, RequestBody.fromInputStream(inputStream, file.getSize()));
    } catch (IOException exception) {
      throw new IllegalStateException("방문 사진 파일을 읽을 수 없습니다.", exception);
    }
  }

  public SignedUrl createSignedGetUrl(String objectKey) {
    GetObjectRequest getObjectRequest =
        GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
    PresignedGetObjectRequest presignedRequest =
        s3Presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(urlExpiration)
                .getObjectRequest(getObjectRequest)
                .build());
    return new SignedUrl(presignedRequest.url().toString(), presignedRequest.expiration());
  }

  public void delete(String objectKey) {
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
  }

  public record SignedUrl(String imageUrl, Instant expiresAt) {}
}
