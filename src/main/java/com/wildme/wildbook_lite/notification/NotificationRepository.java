package com.wildme.wildbook_lite.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadOrderByCreatedAtDesc(
        Long recipientUserId, boolean read, Pageable pageable);

    long countByRecipientUserIdAndRead(Long recipientUserId, boolean read);

    @Modifying
    @Query("update Notification n set n.read = true " +
           "where n.recipientUserId = :userId and n.read = false")
    int markAllAsRead(@Param("userId") Long userId);
}
