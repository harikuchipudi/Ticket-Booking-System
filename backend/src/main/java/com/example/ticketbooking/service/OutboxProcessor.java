package com.example.ticketbooking.service;

import com.example.ticketbooking.model.OutboxEvent;
import com.example.ticketbooking.model.SeatLockEvent;
import com.example.ticketbooking.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxEventRepository outboxEventRepository;
    private final SeatLockService seatLockService;
    private final ObjectMapper objectMapper;

    public OutboxProcessor(OutboxEventRepository outboxEventRepository,
                           SeatLockService seatLockService) {
        this.outboxEventRepository = outboxEventRepository;
        this.seatLockService = seatLockService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Polls the outbox table every 1 second for unprocessed events.
     * This guarantees at-least-once delivery of SSE events even if the Redis
     * connection flakes out during a DB transaction.
     */
    @Scheduled(fixedDelay = 1000)
    public void processOutbox() {
        List<OutboxEvent> events = outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc();
        if (events.isEmpty()) return;

        log.info("Processing {} outbox events...", events.size());

        for (OutboxEvent event : events) {
            try {
                if ("SEAT_BOOKED".equals(event.getEventType())) {
                    SeatLockEvent payload = objectMapper.readValue(event.getPayload(), SeatLockEvent.class);
                    // This handles Redis lock deletion, Cache update, and SSE Broadcast
                    seatLockService.processBookedEvent(payload);
                }

                // Mark as processed (or could be deleted to keep table small)
                event.setProcessed(true);
                outboxEventRepository.save(event);
                
            } catch (Exception e) {
                log.error("Failed to process outbox event id={}", event.getId(), e);
                // We don't mark it as processed, so it will be retried on the next poll.
            }
        }
    }
}
