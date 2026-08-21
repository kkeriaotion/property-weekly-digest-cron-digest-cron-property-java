# Rate-Limited Email and SMS Reminders Explained: Queue Batch Retention for Recovery

**Short answer:** A recoverable shipment-update reminder pipeline separates schedule evaluation, durable queue batch publication, and rate-limited email and SMS workers, then retains recipient envelopes only through a tested replay window.

Sending a shipment update to a media service's subscribers is cheap to enqueue and expensive to get wrong. The right design separates schedule evaluation, durable publication, channel-specific rate limiting, and recovery records; it retains enough evidence to replay a bounded failure without keeping every payload forever. A cron expression may start the planner, but cron isn't the delivery system.

## Retention cost before throughput

Start with the bill. For each campaign, write down four terms: queue operations, retained bytes over time, provider delivery attempts, and observability data. The storage term can be estimated before choosing a queue: `recipient_count x retained_bytes_per_recipient x retention_days`, plus indexes and replicas. In an illustrative shipment-update run with 1,000,000 recipients and a 600-byte immutable envelope per recipient, one day of envelopes is 600 MB before overhead; retaining fourteen daily runs means 8.4 GB before overhead. Those are workload assumptions, not a benchmark. Replace them with measured serialized sizes and the actual fan-out cadence.

The dominant term depends on the system. If the update is sent once and payloads are large, retained envelopes can dominate the queue's storage footprint; if retries are common, delivery attempts and their associated operations can dominate. Either way, changing worker concurrency doesn't erase retained bytes. The useful change is to retain the immutable campaign definition and compact delivery receipts after the replay window, then delete recipient envelopes whose terminal outcome is no longer operationally useful. The catch is immediate: a seven-day replay window cannot reconstruct a recipient-level incident from eight days ago unless another system of record still holds that evidence.

## What should the queue retain for operational recovery?

Keep the smallest record that can answer three recovery questions: what was intended, which subscriber was targeted, and whether a channel reached a terminal outcome. A campaign record should identify the shipment revision and audience snapshot. Each recipient envelope should carry an idempotency key, channel, template revision, destination reference, and eligibility version. Store sensitive addresses behind references when the provider adapter can resolve them at send time; copying full email addresses and phone numbers into every retry record increases both retained bytes and deletion work.

The boundary matters. An immutable envelope makes replay understandable because a retry doesn't silently pick up a changed template or newly computed audience. A mutable profile, by contrast, may be the right source for current consent, since sending after a subscriber opts out is worse than reproducing a historical payload exactly. That split — immutable content intent, current delivery eligibility — needs to be explicit rather than hidden inside a worker.

| Record | Keep through | Recovery value | Cost of deleting earlier |
|---|---|---|---|
| Campaign definition and audience snapshot ID | Audit policy | Rebuilds intent and scope | The original fan-out cannot be proven |
| Recipient envelope | Replay window | Supports targeted replay | Recovery must come from the source of record |
| Attempt detail | Short diagnostic window | Explains pacing and retry decisions | Fine-grained incident diagnosis disappears |
| Compact terminal receipt | Longer than attempt detail | Prevents duplicate replay and reports outcome | Old results become ambiguous |

Retention is not durability.

Retention says how long a record remains available; durability asks what loss the storage design tolerates. A fourteen-day setting on a single ephemeral process is still a weak recovery plan. Define recovery point and recovery time objectives, then test the queue and receipt store against both.

## How should a queue batch control worker concurrency under email and SMS provider limits?

Use separate concurrency budgets per channel and treat a batch as a publication unit, not as permission to start every send at once. Email and SMS providers can have different limits, and a shared pool lets one channel consume the other's capacity. The planner creates deterministic envelopes in bounded batches. Channel workers claim only what their own budget permits. Provider feedback then reduces or delays new claims without blocking unrelated channels.

Keep it boring.

A fixed concurrency cap bounds in-flight work, but it does not by itself enforce a requests-per-time-window limit. If the provider contract is expressed as a rate, place a token bucket or equivalent admission control before the send call; concurrency then protects sockets and memory while the rate limiter protects the provider quota. I'm not sure which term dominates a particular deployment until serialized queue size, send latency, retry frequency, and provider contracts are measured together. That measurement is part of the design, not cleanup after launch.

Cron is useful for initiating a planning pass on a time schedule. It should enqueue a campaign identifier and scheduled occurrence, while a uniqueness constraint prevents the same occurrence from being planned twice. Letting a cron process perform the whole fan-out couples schedule drift to delivery duration and leaves a poor restart boundary. Missed schedules also need an explicit policy: catch up every occurrence, collapse them into the newest shipment state, or expire them. For shipment updates, collapsing may be correct when an older status would mislead subscribers, but legal or contractual notifications may require every occurrence.

## A minimal Python publisher and paced worker

The example below uses generic interfaces on purpose. `queue.publish_many` must durably accept the complete batch or report which envelopes were not accepted; `receipts.begin` must atomically claim an idempotency key; and `limiter.acquire` must enforce the channel's time-window policy. Those contracts carry more architectural weight than the loop syntax.

```python
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Iterable, Protocol


@dataclass(frozen=True)
class Envelope:
    key: str
    campaign_id: str
    subscriber_id: str
    channel: str
    destination_ref: str
    template_revision: str
    eligible_at: datetime


class Queue(Protocol):
    def publish_many(self, messages: list[Envelope]) -> None: ...
    def claim(self, channel: str, limit: int) -> list[Envelope]: ...
    def acknowledge(self, key: str) -> None: ...
    def release(self, key: str, available_at: datetime) -> None: ...


class Receipts(Protocol):
    def begin(self, key: str) -> bool: ...
    def complete(self, key: str, provider_ref: str) -> None: ...
    def abandon(self, key: str) -> None: ...


class Limiter(Protocol):
    def acquire(self, channel: str) -> None: ...


class Provider(Protocol):
    def send(self, envelope: Envelope) -> tuple[str, int | None]: ...


def publish_campaign(queue: Queue, envelopes: Iterable[Envelope], batch_size: int = 500) -> None:
    batch: list[Envelope] = []
    for envelope in envelopes:
        batch.append(envelope)
        if len(batch) == batch_size:
            queue.publish_many(batch)
            batch = []
    if batch:
        queue.publish_many(batch)


def drain_channel(
    queue: Queue,
    receipts: Receipts,
    limiter: Limiter,
    provider: Provider,
    channel: str,
    concurrency: int,
) -> None:
    # Run at most `concurrency` calls in parallel in the process executor.
    for envelope in queue.claim(channel=channel, limit=concurrency):
        if not receipts.begin(envelope.key):
            queue.acknowledge(envelope.key)
            continue

        limiter.acquire(channel)
        outcome, retry_after_seconds = provider.send(envelope)

        if outcome == "delivered":
            receipts.complete(envelope.key, provider_ref=envelope.key)
            queue.acknowledge(envelope.key)
        elif outcome == "rate_limited":
            receipts.abandon(envelope.key)
            delay = retry_after_seconds if retry_after_seconds is not None else 30
            queue.release(
                envelope.key,
                available_at=datetime.now(timezone.utc) + timedelta(seconds=delay),
            )
        else:
            receipts.abandon(envelope.key)
            queue.acknowledge(envelope.key)
```

The sample intentionally leaves transport and storage implementations out, yet it exposes the decisions that must be tested. A real executor would dispatch each claimed envelope to a bounded pool rather than call `provider.send` serially. It would also classify permanent recipient rejection separately from retryable outcomes, record the provider's stable message reference rather than the local key, and use exponential backoff for retryable failures when no authoritative retry delay exists. Backoff reduces repeated contention by spacing attempts progressively; cap it below the envelope's expiry time, add jitter in the implementation, and stop retrying when the message is no longer useful.

## The uncertain-send failure window

There is a sharp edge in `receipts.begin`: if the process stops after the provider accepts a message but before `receipts.complete`, the queue can redeliver an uncertain attempt. No local transaction can atomically include an independent provider. Use the same idempotency key with a provider that honors it, or reconcile the provider reference before replay; otherwise document that duplicates remain possible. The receipt must represent uncertainty rather than overwrite it with a convenient success-or-failure fiction, and a replay operator must be able to isolate these attempts from messages that are known never to have reached the provider.

Exactly-once language hides this ambiguity.

## Recovery drills, observability, and the deliberate loss boundary

A recovery plan is credible only after a drill. Pause the SMS worker while email continues, exhaust the SMS admission budget, restart a worker between provider acceptance and receipt completion, and replay a single failed batch. Verify queue age by channel, oldest eligible envelope, attempts by outcome, limiter wait time, terminal receipt count, and the gap between planned and terminal recipients. Aggregate totals are insufficient because a campaign can look 99% complete while one channel has stopped making progress.

Deployment should preserve the same boundaries. Roll out planner changes separately from worker changes; version envelopes so old workers can reject incompatible work before sending; and canary a new adapter with its own small concurrency and rate budgets. A dead-letter destination can preserve exhausted envelopes, but it isn't a recovery strategy by itself. Assign an owner, an expiry policy, and a replay command that rechecks current eligibility before republishing.

The first instinct is often to retain everything because storage appears inexpensive. The correction comes when deletion requests, indexes, replicas, backups, and incident searches are counted together — raw bytes are only one part of retention cost. Keep verbose attempts just long enough to debug the operational cycle, compact them into terminal receipts, and remove expired recipient envelopes. This deliberately gives up fine-grained history after the diagnostic window. Stick with longer envelope and attempt retention when regulation, contractual proof, or the absence of a trustworthy source of record makes historical reconstruction more valuable than the storage and governance burden.

The decision rule is narrow: choose the shortest retention window that still spans detection, diagnosis, repair, and one verified replay. Then add the longest realistic staff-response delay, because a failure late on Friday does not care that the nominal repair time is two hours. If the resulting window is too expensive, reduce retained payload size or improve recovery automation; don't quietly shorten the promise.

Friday counts.

## References

- Cron overview and scheduling semantics: https://en.wikipedia.org/wiki/Cron
- Exponential backoff overview: https://en.wikipedia.org/wiki/Exponential_backoff
