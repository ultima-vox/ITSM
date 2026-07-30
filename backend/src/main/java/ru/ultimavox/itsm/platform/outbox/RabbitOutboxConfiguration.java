package ru.ultimavox.itsm.platform.outbox;
import org.springframework.amqp.core.*; import org.springframework.context.annotation.*;
@Configuration class RabbitOutboxConfiguration { static final String EXCHANGE="itsm.events"; @Bean TopicExchange itsmEventsExchange(){return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();} }
