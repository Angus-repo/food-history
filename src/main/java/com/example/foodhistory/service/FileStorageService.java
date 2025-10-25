package com.example.foodhistory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageService {
    
    @Value("${app.image.storage.path:./data/images}")
    private String imageStoragePath;
    
    private Path imageStorageLocation;
    
    @PostConstruct
    public void init() {
        this.imageStorageLocation = Paths.get(imageStoragePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.imageStorageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create image storage directory!", ex);
        }
    }
    
    /**
     * 儲存影像檔案
     * @param file 上傳的檔案
     * @param foodId 食物ID
     * @return 儲存的檔案名稱
     */
    public String storeImage(MultipartFile file, Long foodId) throws IOException {
        if (file.isEmpty()) {
            return null;
        }
        
        // 取得檔案副檔名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        // 使用 food ID 作為檔名
        String filename = foodId + extension;
        Path targetLocation = this.imageStorageLocation.resolve(filename);
        
        // 儲存檔案
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        
        return filename;
    }
    
    /**
     * 刪除影像檔案
     * @param filename 檔案名稱
     */
    public void deleteImage(String filename) {
        if (filename == null || filename.isEmpty()) {
            return;
        }
        
        try {
            Path filePath = this.imageStorageLocation.resolve(filename);
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            // 忽略刪除錯誤
        }
    }
    
    /**
     * 取得影像檔案路徑
     * @param filename 檔案名稱
     * @return 檔案路徑
     */
    public Path getImagePath(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        return this.imageStorageLocation.resolve(filename);
    }
    
    /**
     * 檢查影像檔案是否存在
     * @param filename 檔案名稱
     * @return 是否存在
     */
    public boolean imageExists(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        Path filePath = this.imageStorageLocation.resolve(filename);
        return Files.exists(filePath);
    }
}
