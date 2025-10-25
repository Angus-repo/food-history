package com.example.foodhistory.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Component
@Order(1) // 確保在其他初始化之前執行
public class PersistentLoginsMigration implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(PersistentLoginsMigration.class);

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        
        try {
            // 檢查是否需要遷移
            if (needsMigration(jdbcTemplate)) {
                logger.info("開始遷移 persistent_logins 表結構...");
                migratePersistentLogins(jdbcTemplate);
                logger.info("persistent_logins 表結構遷移完成！");
            } else {
                logger.info("persistent_logins 表結構已是最新，無需遷移");
            }
        } catch (Exception e) {
            logger.error("遷移過程發生錯誤", e);
            // 不拋出異常，讓應用程式繼續運行
        }
    }

    private boolean needsMigration(JdbcTemplate jdbcTemplate) {
        try {
            // 檢查 persistent_logins 表是否存在
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'PERSISTENT_LOGINS'",
                Integer.class
            );
            
            // 檢查是否有 id 欄位
            Integer idColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'PERSISTENT_LOGINS' AND COLUMN_NAME = 'ID'",
                Integer.class
            );
            
            // 如果沒有 id 欄位，需要遷移
            return idColumnCount == 0;
        } catch (Exception e) {
            // 如果表不存在，不需要遷移（Hibernate 會建立）
            return false;
        }
    }

    private void migratePersistentLogins(JdbcTemplate jdbcTemplate) {
        // 1. 備份現有資料到臨時表
        logger.info("步驟 1: 備份現有資料到臨時表...");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS persistent_logins_backup AS SELECT * FROM persistent_logins");
        
        List<Map<String, Object>> backupData = jdbcTemplate.queryForList("SELECT * FROM persistent_logins_backup");
        logger.info("已備份 {} 筆資料", backupData.size());
        
        // 2. 刪除舊表
        logger.info("步驟 2: 刪除舊表...");
        jdbcTemplate.execute("DROP TABLE IF EXISTS persistent_logins");
        
        // 3. 建立新表結構
        logger.info("步驟 3: 建立新表結構...");
        jdbcTemplate.execute(
            "CREATE TABLE persistent_logins (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  series VARCHAR(64) NOT NULL UNIQUE," +
            "  username VARCHAR(64) NOT NULL," +
            "  token VARCHAR(64) NOT NULL," +
            "  last_used TIMESTAMP NOT NULL," +
            "  ip VARCHAR(45)" +
            ")"
        );
        
        // 4. 從備份恢復資料
        if (!backupData.isEmpty()) {
            logger.info("步驟 4: 從備份恢復資料...");
            for (Map<String, Object> row : backupData) {
                jdbcTemplate.update(
                    "INSERT INTO persistent_logins (series, username, token, last_used, ip) VALUES (?, ?, ?, ?, ?)",
                    row.get("SERIES"),
                    row.get("USERNAME"),
                    row.get("TOKEN"),
                    row.get("LAST_USED"),
                    null // 舊資料沒有 ip 欄位，設為 null
                );
            }
            logger.info("已恢復 {} 筆資料", backupData.size());
        }
        
        // 5. 刪除備份表
        logger.info("步驟 5: 清理備份表...");
        jdbcTemplate.execute("DROP TABLE IF EXISTS persistent_logins_backup");
        
        logger.info("遷移完成！");
    }
}
