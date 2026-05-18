package com.example.ticketbooking.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.ticketbooking.repository.TicketRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency stress test for the distributed seat locking mechanism.
 *
 * Test: 50 threads simultaneously attempt to lock the SAME seat.
 *       Only EXACTLY ONE should succeed — the Redis SETNX guarantee.
 *
 * Prerequisites: Docker + Redis must be running locally on port 6379.
 *   Start with: docker-compose up -d
 *
 * This test intentionally uses the REAL Redis client (Lettuce) — not mocked.
 * Mocking Redis would invalidate the test; the whole point is proving SETNX
 * behaves correctly under true concurrent load.
 */
class SeatLockConcurrencyTest {

    private static final String TEST_SEAT    = "CONCURRENT-TEST-SEAT-1";
    private static final int    NUM_THREADS  = 50;

    private SeatLockService         seatLockService;
    private StringRedisTemplate     redisTemplate;
    private LettuceConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() {
        // Connect to local Redis (must be running via docker-compose up -d)
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        // Mock TicketRepository — not needed for the lock test
        TicketRepository ticketRepository = Mockito.mock(TicketRepository.class);
        Mockito.when(ticketRepository.findAll()).thenReturn(java.util.List.of());

        seatLockService = new SeatLockService(redisTemplate, ticketRepository, new SimpleMeterRegistry());

        // Ensure test seat key is clean before the test
        redisTemplate.delete("seat:lock:MI-vs-CSK:" + TEST_SEAT);
    }

    @AfterEach
    void tearDown() {
        // Clean up so subsequent test runs start fresh
        redisTemplate.delete("seat:lock:MI-vs-CSK:" + TEST_SEAT);
        connectionFactory.destroy();
    }

    @Test
    void only_one_of_fifty_concurrent_threads_should_win_the_lock() throws InterruptedException {
        AtomicInteger wins     = new AtomicInteger(0);
        AtomicInteger losses   = new AtomicInteger(0);
        CountDownLatch ready   = new CountDownLatch(NUM_THREADS);
        CountDownLatch go      = new CountDownLatch(1);
        CountDownLatch done    = new CountDownLatch(NUM_THREADS);
        ExecutorService pool   = Executors.newFixedThreadPool(NUM_THREADS);

        for (int i = 0; i < NUM_THREADS; i++) {
            final String userId = "stress-user-" + i;
            pool.submit(() -> {
                try {
                    ready.countDown();   // signal this thread is ready
                    go.await();          // wait for all threads to be ready — maximise concurrency
                    boolean acquired = seatLockService.lock("MI-vs-CSK", TEST_SEAT, userId);
                    if (acquired) wins.incrementAndGet();
                    else          losses.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();       // wait until all 50 threads are staged at go.await()
        go.countDown();      // release all threads simultaneously
        done.await(10, TimeUnit.SECONDS); // wait for all to finish

        pool.shutdown();

        // EXACTLY one winner — the atomic SETNX guarantee
        assertThat(wins.get())
                .as("Exactly one thread should win the distributed lock")
                .isEqualTo(1);

        assertThat(losses.get())
                .as("All other threads should have been rejected")
                .isEqualTo(NUM_THREADS - 1);

        System.out.printf(
            "%n✅ Concurrency test passed: %d threads competed, %d won, %d rejected%n",
            NUM_THREADS, wins.get(), losses.get()
        );
    }
}
