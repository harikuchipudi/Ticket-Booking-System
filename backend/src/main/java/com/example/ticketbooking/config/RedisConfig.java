package com.example.ticketbooking.config;

import com.example.ticketbooking.service.SeatLockService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisConfig {

    public static final String SEAT_UPDATES_TOPIC = "seat-updates-topic";

    @Bean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                            MessageListenerAdapter listenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic(SEAT_UPDATES_TOPIC));
        return container;
    }

    @Bean
    MessageListenerAdapter listenerAdapter(SeatLockService seatLockService) {
        // When a message is received on the topic, it will call SeatLockService.receiveRedisMessage()
        return new MessageListenerAdapter(seatLockService, "receiveRedisMessage");
    }
}
