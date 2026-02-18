package com.vnfm.vimmanager.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessedCommandRepository extends JpaRepository<ProcessedCommand, Long> {

    Optional<ProcessedCommand> findByMessageId(String messageId);

    boolean existsByMessageId(String messageId);
}
