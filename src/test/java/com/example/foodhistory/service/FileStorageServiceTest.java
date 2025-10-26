package com.example.foodhistory.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.foodhistory.service.FileStorageService.class)
@TestPropertySource(properties = {
    "app.image.storage.path=target/test-images"
})
class FileStorageServiceTest {

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    void testStoreAndRetrieveImage() throws Exception {
        // 創建測試圖片
        byte[] imageData = "test image content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.jpg",
            "image/jpeg",
            imageData
        );

        // 儲存圖片
        String filename = fileStorageService.storeImage(file, 1L);
        assertNotNull(filename);
        assertTrue(filename.contains("1"));

        // 檢查檔案是否存在
        assertTrue(fileStorageService.imageExists(filename));

        // 取得檔案路徑
        Path imagePath = fileStorageService.getImagePath(filename);
        assertNotNull(imagePath);
        assertTrue(Files.exists(imagePath));

        // 刪除圖片
        fileStorageService.deleteImage(filename);
        assertFalse(fileStorageService.imageExists(filename));
    }

    @Test
    void testDeleteNonExistentImage() {
        // 應該不會拋出異常
        assertDoesNotThrow(() -> fileStorageService.deleteImage("nonexistent.jpg"));
    }

    @Test
    void testEmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file",
            "empty.jpg",
            "image/jpeg",
            new byte[0]
        );

        String filename = fileStorageService.storeImage(emptyFile, 999L);
        assertNull(filename);
    }
}
