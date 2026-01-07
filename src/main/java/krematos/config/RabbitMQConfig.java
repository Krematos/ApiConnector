package krematos.config;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
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
    // (Příjemce) Receiver pro přijímání zpráv z RabbitMQ
    @Bean
    public Receiver receiver(@Qualifier("monoConnectionFactory") ConnectionFactory connectionFactory) {
        return RabbitFlux.createReceiver(new ReceiverOptions()
                .connectionFactory(connectionFactory)
                .connectionSubscriptionScheduler(Schedulers.boundedElastic()));
    }
    // (Připojení) ConnectionFactory pro reaktivní přístup k RabbitMQ
    @Bean
    public ConnectionFactory monoConnectionFactory(@Value("${spring.rabbitmq.host}") String host,
                                               @Value("${spring.rabbitmq.port:5672}") int port,
                                               @Value("${spring.rabbitmq.username}") String username,
                                               @Value("${spring.rabbitmq.password}") String password) {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        connectionFactory.useNio(); //  Použití NIO pro neblokující IO
        return connectionFactory;
    }
    // (Odesílatel) Sender pro odesílání zpráv do RabbitMQ
    @Bean
    public Sender sender(@Qualifier("monoConnectionFactory") ConnectionFactory connectionFactory) {
        return RabbitFlux.createSender(new SenderOptions()
                .connectionFactory(connectionFactory)
                .resourceManagementScheduler(Schedulers.boundedElastic()));
    }
}
