package krematos.config;

import com.rabbitmq.client.Connection;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.*;


@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue retryQueue() {
        return new Queue("failed.transaction.queue", true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange("failed.transaction.exchange");
    }

    @Bean
    public Binding binding(Queue retryQueue, DirectExchange exchange) {
        return BindingBuilder
                .bind(retryQueue)
                .to(exchange)
                .with("failed.transaction.routingkey");
    }

    @Bean
    public Receiver receiver(Mono<Connection> connectionMono) {
        return RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(connectionMono));
    }

    @Bean
    public Mono<Connection> connectionMono(CachingConnectionFactory connectionFactory) {
        return Mono.fromCallable(() -> connectionFactory.getRabbitConnectionFactory().newConnection()).cache();
    }

    @Bean
    public Sender sender(Mono<Connection> connectionMono) {
        return RabbitFlux.createSender(new SenderOptions().connectionMono(connectionMono));
    }
}
