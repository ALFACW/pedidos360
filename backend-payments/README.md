# Payments service

Consumes `ORDER_CREATED` events, simulates payment authorization, and publishes `PAYMENT_APPROVED` or `PAYMENT_REJECTED` results. The service owns its processing logic and does not access the orders service data store.

Configure `RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` before running. Apply the `payment_inbox` table from `database/schema.sql` to PostgreSQL:

```powershell
mvn spring-boot:run
```

The consumer retries transient failures. Processed order event IDs are stored in PostgreSQL in the same transaction as publishing the payment result, so RabbitMQ redeliveries do not repeat payment processing. Messages that still fail are routed to its durable dead-letter queue for inspection and controlled replay.
