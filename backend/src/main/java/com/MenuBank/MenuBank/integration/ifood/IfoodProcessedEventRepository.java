package com.MenuBank.MenuBank.integration.ifood;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface IfoodProcessedEventRepository extends JpaRepository<IfoodProcessedEvent, String> {

    /**
     * Returns, in a single query, which of the given event ids were already processed.
     */
    @Query("select e.eventId from IfoodProcessedEvent e where e.eventId in :eventIds")
    List<String> findExistingIds(@Param("eventIds") Collection<String> eventIds);

    @Modifying
    @Transactional
    @Query("delete from IfoodProcessedEvent e where e.processedAt < :threshold")
    int deleteProcessedBefore(@Param("threshold") LocalDateTime threshold);
}
