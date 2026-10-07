# Notifications service

Consumes `PAYMENT_APPROVED` and `PAYMENT_REJECTED` events from RabbitMQ and emails the customer using the address carried in the order event. The event ID is persisted before processing so redeliveries do not send another email.

## Run locally

Configure `RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and the `MAIL_*` settings. Apply `database/schema.sql` to PostgreSQL and start RabbitMQ and an SMTP server. For a local end-to-end run, start the repository using Mailpit:

```powershell
docker compose --env-file .env -f docker-compose.yml -f docker-compose.mailpit.yml up -d --build
```

Open `http://localhost:8025` to inspect captured emails. To run the service outside Docker, use `MAIL_HOST=localhost` and `MAIL_PORT=1025`. In deployments, configure `MAIL_HOST`, port, credentials, sender, authentication and STARTTLS for the selected SMTP provider (for example, Amazon SES SMTP); keep credentials in the environment and never commit them.

RabbitMQ retries temporary processing failures up to five attempts; messages that still fail are routed to the notification dead-letter queue. SMTP failures are propagated so the database transaction rolls back and RabbitMQ can retry. Successfully handled event IDs are retained in PostgreSQL, preventing duplicate emails on redelivery.
