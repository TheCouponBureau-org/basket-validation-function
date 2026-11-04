# Dockerized Basket Validation API

This is the smallest production-oriented Express wrapper around the current
JavaScript SDK in `../js`. One Docker Compose command starts:

- the Express API;
- Redis with persistent storage;
- a 23-hour TCB access-token cache;
- an initial six-month MOF synchronization on first startup;
- incremental MOF synchronization for the recent two-day window every four hours.

Validation resolves scanned GS1 values, loads purchase requirements from Redis,
checks coupon status with TCB, and preserves the caller's coupon order. If an MOF
record is not yet in Redis, validation safely falls back to TCB for that coupon.

## Start

From the repository root:

```bash
cp docker/.env.example docker/.env
```

Set `TCB_ENDPOINT`, `TCB_ACCESS_KEY`, and `TCB_SECRET_KEY` in `docker/.env`, then:

```bash
cd docker
docker compose up --build -d
docker compose logs -f api
```

The service is available at `http://localhost:3000`. Redis data is retained in
the `tcb-redis-data` Docker volume.

## MOF synchronization

On the first successful startup sync, the server requests the previous six
months. After that, startup and scheduled incremental runs refresh the recent
two-day window. Every page is stored as:

```text
tcb:mof:<base_gs1> -> purchase_requirement JSON
```

`MOF_SYNC_CRON` uses standard five-field cron syntax. The default
`0 */4 * * *` runs at minute zero every four hours in `TZ` (UTC by default).
Set `MOF_SYNC_ON_START=false` only when startup synchronization is managed
elsewhere.

## API

TCB credentials come from environment variables, so callers do not send keys
or tokens in request headers.

### Get the cached access token

```bash
curl -X POST http://localhost:3000/get_access_token
```

```json
{ "token": "YOUR_ACCESS_TOKEN" }
```

### Validate coupons for a basket

```bash
curl -X POST http://localhost:3000/coupons_valid_for_basket \
  -H 'Content-Type: application/json' \
  -d '{
    "basket": [
      { "product_code": "037000930396", "price": 1.29, "quantity": 2, "unit": "item" },
      { "product_code": "037000934677", "price": 1.34, "quantity": 2, "unit": "item" }
    ],
    "coupons": [
      "8112009988459000019133924009755364",
      "8112209988459000350001"
    ]
  }'
```

```json
{
  "basket_validation_output": {
    "discount_in_cents": 100,
    "applied_coupons": [
      {
        "coupon_code": "8112009988459000019133924009755364",
        "face_value_in_cents": 100,
        "product_codes": { "gtins": ["037000930396"] }
      }
    ]
  },
  "execution_time_in_ms": 42
}
```

### Redeem applied coupons

Call only after the retail transaction succeeds.

```bash
curl -X POST http://localhost:3000/redeem_coupons \
  -H 'Content-Type: application/json' \
  -d '{
    "coupons": ["8112009988459000019133924009755364"]
  }'
```

### Roll back a voided transaction

```bash
curl -X POST http://localhost:3000/rollback_coupons \
  -H 'Content-Type: application/json' \
  -d '{
    "coupons": ["8112009988459000019133924009755364"]
  }'
```

Rollback is allowed only within the TCB rollback window.

## Accelerator mode

Add these fields to validation or redemption request bodies:

```json
{
  "mode": "accelerator",
  "retailer_email_domain": "retailer.example"
}
```

If `retailer_email_domain` is present and `mode` is omitted, the server selects
accelerator mode automatically. Rollback accepts `mode` but does not require the
retailer domain.

## Operations

```bash
docker compose ps
docker compose logs -f api
docker compose restart api
docker compose down
```

Use `docker compose down -v` only when you intentionally want to delete the
persisted Redis MOF cache.
