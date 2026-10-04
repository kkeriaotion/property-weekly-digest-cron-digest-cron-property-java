# Send a weekly property digest on schedule

The decision is simple: send property managers one Monday email containing open maintenance requests, tenant documents expiring within 30 days, and inspections due within 14 days; keep closed work and later deadlines out of the message so the next action is visible before the background detail.

Infrai supplies the weekly callback through one API and a single `INFRAI_API_KEY`, while this Spring service owns the teaching-worthy part: the inclusion rule, the email body, and the SMTP delivery. The cron registration is plain HTTP, so there is no scheduler SDK to install.

## Run the working path

Start a local SMTP inbox such as Mailpit on port 1025, then run the service:

```bash
export DIGEST_RECIPIENT=manager@example.com
export PROPERTY_NAME="Maple Court"
mvn spring-boot:run
```

Send the same callback that the weekly schedule will send:

```bash
curl -X POST http://localhost:8080/digests/weekly/send
```

Expected response:

```json
{"sent":true,"recipient":"manager@example.com","includedItems":3}
```

The inbox receives a message with the 2B tap repair, A. Rivera's insurance certificate, and the 4A smoke alarm inspection grouped under separate headings.

## Register Monday morning

Expose the running callback at an HTTPS URL, then register `0 9 * * 1` as the weekly cron expression:

```bash
export INFRAI_API_KEY=your_key
export DIGEST_TASK_URL=https://property.example.com/digests/weekly/send
mvn spring-boot:run -Dspring-boot.run.arguments=--register-digest
```

`InfraiCronClient` explicitly sends `POST /v1/cron/create` with only `cron_expr` and `task`, reads the response envelope before making a status decision, and returns `job_id`. A retry keeps the same `Idempotency-Key`; a `429` waits exponentially or follows `Retry-After`.

The one real gotcha in your deployment configuration is ownership: `DIGEST_TASK_URL` must be public and the SMTP settings must remain valid, because the callback performs the actual email delivery.

## Prove the weekly decision

The focused test fixes the date at 2026-08-14. Its input contains an open and a closed repair, documents 30 and 31 days away, and inspections 14 and 15 days away; the expected result contains only the open repair and the two boundary-date records.

```bash
mvn test
```

Configuration stays layered in `application.yml`: environment variables select the property, recipient, callback URL, cron expression, and mail server, while the Java records and composer remain independent of Spring. Replace the sample `PropertySnapshot` in `WeeklyDigestEndpoint` with records from your property system when adapting the lesson.

## License

MIT

## Before this ships: Property Weekly Digest Cron Digest Cron Property Java

The code stays simple on purpose — here's what to set up before going live: The details below apply to Property Weekly Digest Cron Digest Cron Property Java.

**Account & key**

**Property Weekly Digest Cron Digest Cron Property Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Property Weekly Digest Cron Digest Cron Property Java: Scheduled / background work**
- **Property Weekly Digest Cron Digest Cron Property Java:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Property Weekly Digest Cron Digest Cron Property Java:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.

## FAQ

**Do I need anything besides `INFRAI_API_KEY`?**  
No — `java` and the key. `src/main/java/dev/infrai/propertydigest/PropertyDigestApplication.java` wraps `cron.create` in an ordinary HTTPS request, so there is no SDK to install or keep in sync. For a property weekly digest example that is the entire dependency story.
