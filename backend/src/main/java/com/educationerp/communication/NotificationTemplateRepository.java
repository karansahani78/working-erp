package com.educationerp.communication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    List<NotificationTemplate> findAllByOrderByEventCodeAscChannelAsc();

    List<NotificationTemplate> findByActiveTrueOrderByEventCodeAsc();

    Optional<NotificationTemplate> findByCodeIgnoreCase(String code);

    Optional<NotificationTemplate> findByEventCodeAndChannel(String eventCode, Channel channel);

    /**
     * The template an event actually sends through. An inactive template is deliberately
     * absent: retiring a message must stop it being sent without deleting its history.
     */
    Optional<NotificationTemplate> findByEventCodeAndChannelAndActiveTrue(String eventCode, Channel channel);

    boolean existsByEventCodeAndChannel(String eventCode, Channel channel);

    boolean existsByCodeIgnoreCase(String code);
}
