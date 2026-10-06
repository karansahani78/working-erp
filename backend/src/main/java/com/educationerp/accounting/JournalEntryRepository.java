package com.educationerp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByEntryNumberIgnoreCase(String entryNumber);

    List<JournalEntry> findByStatusOrderByEntryDateAsc(JournalEntry.Status status);

    List<JournalEntry> findByEntryDateBetweenOrderByEntryDateAsc(LocalDate from, LocalDate to);

    List<JournalEntry> findBySourceTypeAndSourceId(JournalEntry.SourceType sourceType, UUID sourceId);
}
