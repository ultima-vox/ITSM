package ru.ultimavox.itsm.platform.outbox;
import java.sql.ResultSet; import java.time.Instant; import java.util.*; import org.springframework.amqp.AmqpException; import org.springframework.amqp.rabbit.core.RabbitTemplate; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
/** At-least-once relay. A failed publish leaves the outbox record pending; consumer-side event ID deduplication is mandatory. */
@Component class RabbitOutboxRelay {
 private final JdbcTemplate jdbc; private final RabbitTemplate rabbit;
 RabbitOutboxRelay(JdbcTemplate jdbc,RabbitTemplate rabbit){this.jdbc=jdbc;this.rabbit=rabbit;}
 @Scheduled(fixedDelayString="${itsm.outbox.poll-interval:PT5S}") void relay(){List<Pending> pending=jdbc.query("SELECT id,event_type,payload::text,attempts FROM outbox_event WHERE published_at IS NULL ORDER BY occurred_at LIMIT 100",(rs,row)->map(rs));for(Pending item:pending) publish(item);}
 private Pending map(ResultSet rs)throws java.sql.SQLException{return new Pending(UUID.fromString(rs.getString("id")),rs.getString("event_type"),rs.getString("payload"),rs.getInt("attempts"));}
 private void publish(Pending item){try{rabbit.convertAndSend(RabbitOutboxConfiguration.EXCHANGE,item.type(),item.payload(),message->{message.getMessageProperties().setMessageId(item.id().toString());message.getMessageProperties().setHeader("event_type",item.type());return message;});jdbc.update("UPDATE outbox_event SET published_at=now(), last_error=NULL WHERE id=? AND published_at IS NULL",item.id());}catch(AmqpException exception){jdbc.update("UPDATE outbox_event SET attempts=attempts+1,last_error=? WHERE id=? AND published_at IS NULL",truncate(exception.getMessage()),item.id());}}
 private String truncate(String message){return message==null?"Unknown AMQP error":message.substring(0,Math.min(message.length(),1000));}
 record Pending(UUID id,String type,String payload,int attempts) {}
}
