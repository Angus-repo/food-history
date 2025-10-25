package com.example.foodhistory.repository;

import com.example.foodhistory.model.PersistentLogin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PersistentLoginRepository extends JpaRepository<PersistentLogin, Long> {
    
    @Transactional
    @Modifying
    @Query("UPDATE PersistentLogin p SET p.ip = :ip WHERE p.series = :series")
    void updateIpAddress(String series, String ip);
}
