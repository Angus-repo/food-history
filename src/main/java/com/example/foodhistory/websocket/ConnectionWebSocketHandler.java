package com.example.foodhistory.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * WebSocket 連線處理器
 * 用於即時偵測客戶端與伺服器的連線狀態
 */
@Component
public class ConnectionWebSocketHandler extends TextWebSocketHandler {
    
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("[WebSocket] 新連線建立: " + session.getId());
        
        // 立即發送連線確認訊息
        String connectedMessage = "{\"type\":\"connected\",\"timestamp\":" + System.currentTimeMillis() + "}";
        session.sendMessage(new TextMessage(connectedMessage));
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("[WebSocket] 連線已關閉: " + session.getId() + ", 狀態: " + status);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        
        // 處理客戶端的 ping 訊息
        if (payload.contains("\"type\":\"ping\"")) {
            String pongMessage = "{\"type\":\"pong\",\"timestamp\":" + System.currentTimeMillis() + "}";
            session.sendMessage(new TextMessage(pongMessage));
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.out.println("[WebSocket] 傳輸錯誤: " + session.getId() + ", 錯誤: " + exception.getMessage());
        sessions.remove(session);
    }
    
    /**
     * 定期發送心跳（每 20 秒）
     * 確保連線保持活躍，並讓客戶端知道伺服器仍在線上
     */
    @Scheduled(fixedRate = 20000)
    public void sendHeartbeat() {
        String heartbeatMessage = "{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}";
        TextMessage textMessage = new TextMessage(heartbeatMessage);
        
        List<WebSocketSession> deadSessions = new java.util.ArrayList<>();
        
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    deadSessions.add(session);
                }
            } else {
                deadSessions.add(session);
            }
        }
        
        sessions.removeAll(deadSessions);
    }
    
    /**
     * 廣播訊息給所有連線的客戶端
     */
    public void broadcast(String type, String data) {
        String message = "{\"type\":\"" + type + "\",\"data\":" + data + ",\"timestamp\":" + System.currentTimeMillis() + "}";
        TextMessage textMessage = new TextMessage(message);
        
        List<WebSocketSession> deadSessions = new java.util.ArrayList<>();
        
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    deadSessions.add(session);
                }
            } else {
                deadSessions.add(session);
            }
        }
        
        sessions.removeAll(deadSessions);
    }
    
    /**
     * 獲取當前連線數
     */
    public int getConnectionCount() {
        return sessions.size();
    }
}
