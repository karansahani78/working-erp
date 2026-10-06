package com.educationerp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {

    List<JournalLine> findByJournalEntryIdOrderByIdAsc(UUID journalEntryId);

    void deleteByJournalEntryId(UUID journalEntryId);

    /**
     * Closing balance for one account: total debits less total credits. Only posted entries
     * count, and a reversed entry still counts because the reversal that cancelled it is a
     * separate entry. A draft is nobody's liability until it is posted, so it is excluded.
     */
    @Query("select coalesce(sum(l.debit), 0) - coalesce(sum(l.credit), 0) from JournalLine l "
            + "where l.accountId = :accountId and l.journalEntryId in ("
            + "select e.id from JournalEntry e where e.status in :statuses)")
    BigDecimal balanceOf(@Param("accountId") UUID accountId,
                         @Param("statuses") List<JournalEntry.Status> statuses);
}
