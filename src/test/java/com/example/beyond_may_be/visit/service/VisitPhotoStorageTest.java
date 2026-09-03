package com.example.beyond_may_be.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class VisitPhotoStorageTest {

  @Mock private S3Client s3Client;
  @Mock private S3Presigner s3Presigner;

  @DisplayName("비공개 S3 객체를 저장하고 정확히 1시간 유효한 GET URL을 서명한다.")
  @Test
  void uploadAndSign_usesPrivateBucketAndConfiguredExpiration() throws Exception {
    PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
    Instant expiresAt = Instant.parse("2026-08-15T06:33:00Z");
    given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .willReturn(presignedRequest);
    given(presignedRequest.url())
        .willReturn(
            URI.create("https://private-bucket.s3.amazonaws.com/visits/9001/photo").toURL());
    given(presignedRequest.expiration()).willReturn(expiresAt);
    VisitPhotoStorage storage =
        new VisitPhotoStorage(s3Client, s3Presigner, "private-bucket", Duration.ofHours(1));
    MockMultipartFile file =
        new MockMultipartFile("file", "visit.png", "image/png", new byte[] {1, 2, 3});

    storage.upload("visits/9001/photo", "image/png", file);
    VisitPhotoStorage.SignedUrl signedUrl = storage.createSignedGetUrl("visits/9001/photo");
    storage.delete("visits/9001/photo");

    ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
    then(s3Client).should().putObject(putCaptor.capture(), any(RequestBody.class));
    assertThat(putCaptor.getValue().bucket()).isEqualTo("private-bucket");
    assertThat(putCaptor.getValue().key()).isEqualTo("visits/9001/photo");
    assertThat(putCaptor.getValue().contentType()).isEqualTo("image/png");

    ArgumentCaptor<GetObjectPresignRequest> signCaptor =
        ArgumentCaptor.forClass(GetObjectPresignRequest.class);
    then(s3Presigner).should().presignGetObject(signCaptor.capture());
    assertThat(signCaptor.getValue().signatureDuration()).isEqualTo(Duration.ofHours(1));
    assertThat(signCaptor.getValue().getObjectRequest().bucket()).isEqualTo("private-bucket");
    assertThat(signCaptor.getValue().getObjectRequest().key()).isEqualTo("visits/9001/photo");
    assertThat(signedUrl.imageUrl()).contains("private-bucket.s3.amazonaws.com");
    assertThat(signedUrl.expiresAt()).isEqualTo(expiresAt);

    then(s3Client)
        .should()
        .deleteObject(
            DeleteObjectRequest.builder()
                .bucket("private-bucket")
                .key("visits/9001/photo")
                .build());
  }
}
