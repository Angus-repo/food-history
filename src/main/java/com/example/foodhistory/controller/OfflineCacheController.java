package com.example.foodhistory.controller;

import com.example.foodhistory.model.Food;
import com.example.foodhistory.service.FoodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 離線快取 API 控制器
 * 提供前端 Service Worker 所需的資料快取端點
 */
@RestController
@RequestMapping("/api/foods")
public class OfflineCacheController {
    
    @Autowired
    private FoodService foodService;
    
    // 快取版本號（每次資料結構變更或重大更新時遞增）
    private static final String CACHE_VERSION = "v1.0.0";
    
    // 上次資料更新時間（實際應用中應從資料庫或快取中取得）
    private volatile long lastDataUpdateTimestamp = System.currentTimeMillis();
    
    // 儲存所有 SSE 連線
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    
    /**
     * 健康檢查端點 - 用於快速確認伺服器是否可達
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
    
    /**
     * SSE 連線端點 - 用於即時監測伺服器連線狀態
     * 當連線建立時，前端就知道 server 在線
     * 當連線斷開時，前端就知道 server 離線
     */
    @GetMapping(value = "/connection-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectionStream() {
        // 設定 0 表示無超時（由心跳機制維護連線）
        SseEmitter emitter = new SseEmitter(0L);
        
        emitters.add(emitter);
        
        // 立即發送連線確認事件
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\",\"timestamp\":" + System.currentTimeMillis() + "}"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        
        // 啟動心跳任務 - 每 25 秒發送一次心跳
        java.util.concurrent.ScheduledExecutorService scheduler = 
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        java.util.concurrent.ScheduledFuture<?> heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("{\"timestamp\":" + System.currentTimeMillis() + "}"));
            } catch (IOException e) {
                // 連線已斷開，取消心跳任務
                scheduler.shutdown();
            }
        }, 25, 25, java.util.concurrent.TimeUnit.SECONDS);
        
        // 設定完成、超時、錯誤時的清理
        Runnable cleanup = () -> {
            emitters.remove(emitter);
            heartbeatTask.cancel(true);
            scheduler.shutdown();
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        
        return emitter;
    }
    
    /**
     * 向所有連線的客戶端發送事件（例如資料更新通知）
     */
    public void broadcastEvent(String eventName, String data) {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }
        
        emitters.removeAll(deadEmitters);
    }
    
    /**
     * 獲取所有食物資料用於離線快取
     * 這個端點返回完整的食物列表，供 Service Worker 預載
     */
    @GetMapping("/offline-cache")
    public ResponseEntity<Map<String, Object>> getOfflineCacheData() {
        List<Food> allFoods = foodService.getAllFoods();
        
        // 轉換為適合前端快取的格式
        List<Map<String, Object>> foodsData = allFoods.stream()
                .map(this::convertFoodToMap)
                .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("foods", foodsData);
        response.put("totalCount", allFoods.size());
        response.put("isCompleteDataset", true);
        response.put("cacheVersion", CACHE_VERSION);
        response.put("timestamp", Instant.now().toString());
        response.put("lastUpdate", lastDataUpdateTimestamp);
        
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .body(response);
    }
    
    /**
     * 獲取快取版本資訊
     * 用於檢查是否需要更新快取
     */
    @GetMapping("/cache-version")
    public ResponseEntity<Map<String, Object>> getCacheVersion() {
        Map<String, Object> response = new HashMap<>();
        response.put("version", CACHE_VERSION);
        response.put("timestamp", Instant.now().toString());
        response.put("lastUpdate", lastDataUpdateTimestamp);
        response.put("totalFoods", foodService.getAllFoods().size());
        
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .body(response);
    }
    
    /**
     * 伺服器連線檢測端點
     * 用於前端判斷是否能連線到伺服器（而非僅檢測網路）
     */
    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.ok().build();
    }
    
    /**
     * 獲取所有圖片 URL 列表
     * 用於預載圖片到快取
     */
    @GetMapping("/image-list")
    public ResponseEntity<Map<String, Object>> getImageList() {
        List<Food> allFoods = foodService.getAllFoods();
        
        List<String> imageUrls = allFoods.stream()
                .filter(food -> food.getImagePath() != null && !food.getImagePath().isEmpty())
                .map(food -> "/foods/images/" + food.getImagePath())
                .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("images", imageUrls);
        response.put("count", imageUrls.size());
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .body(response);
    }
    
    /**
     * 通知快取資料已更新
     * 當食物資料被修改時，可以調用這個方法更新時間戳
     */
    public void notifyDataUpdated() {
        this.lastDataUpdateTimestamp = System.currentTimeMillis();
    }
    
    /**
     * 將 Food 實體轉換為 Map，適合前端使用
     */
    private Map<String, Object> convertFoodToMap(Food food) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", food.getId());
        map.put("name", food.getName());
        map.put("carbGrams", food.getCarbGrams());
        map.put("coefficient", food.getCoefficient());
        map.put("quantity", food.getQuantity());
        map.put("unit", food.getUnit());
        map.put("notes", food.getNotes());
        map.put("imagePath", food.getImagePath());
        map.put("imageContentType", food.getImageContentType());
        map.put("isFavorite", food.getIsFavorite());
        return map;
    }
}
