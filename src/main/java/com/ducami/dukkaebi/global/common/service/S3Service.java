package com.ducami.dukkaebi.global.common.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
public class S3Service implements StorageService {
    private final AmazonS3Client amazonS3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    /**
     * S3에 파일 업로드
     * @param file 업로드할 파일
     * @param folder S3 내 폴더 경로 (예: "contest", "notice")
     * @return 업로드된 파일의 URL
     */
    @Override
    public String uploadFile(MultipartFile file, String folder) {
        String fileName = createFileName(file.getOriginalFilename(), folder);

        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(file.getSize());
        objectMetadata.setContentType(file.getContentType());

        try (InputStream inputStream = file.getInputStream()) {
            amazonS3Client.putObject(new PutObjectRequest(bucketName, fileName, inputStream, objectMetadata)
                    .withCannedAcl(CannedAccessControlList.PublicRead));
        } catch (IOException e) {
            log.error("S3 파일 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("파일 업로드에 실패했습니다.", e);
        }

        return amazonS3Client.getUrl(bucketName, fileName).toString();
    }

    /**
     * S3에서 파일 삭제
     * @param fileUrl 삭제할 파일의 URL
     */
    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }

        try {
            String fileName = extractFileNameFromUrl(fileUrl);
            amazonS3Client.deleteObject(new DeleteObjectRequest(bucketName, fileName));
            log.info("S3 파일 삭제 성공: {}", fileName);
        } catch (Exception e) {
            log.error("S3 파일 삭제 실패: {}", e.getMessage());
            throw new RuntimeException("파일 삭제에 실패했습니다.", e);
        }
    }

    /**
     * URL에서 파일명 추출
     * @param fileUrl 파일 URL
     * @return 추출된 파일명
     */
    @Override
    public String extractFileNameFromUrl(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("파일 URL이 비어 있습니다.");
        }

        AmazonS3URI s3Uri;
        try {
            s3Uri = new AmazonS3URI(URI.create(fileUrl));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("올바르지 않은 S3 파일 URL입니다.", e);
        }

        if (!bucketName.equals(s3Uri.getBucket())) {
            throw new IllegalArgumentException("현재 S3 버킷의 파일 URL이 아닙니다.");
        }

        String fileName = s3Uri.getKey();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("S3 파일 경로가 비어 있습니다.");
        }
        return fileName;
    }
}
