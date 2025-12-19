package krematos.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



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
}
