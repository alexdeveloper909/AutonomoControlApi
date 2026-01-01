# API Usage Guide

This document explains how to call the Lambda-backed REST API and how to wire the routes in API Gateway (HTTP API v2). It assumes Cognito JWT authorizer is used and the `sub` claim is present.

## Base assumptions

- All endpoints are under the same Lambda handler: `autonomo.handler.RecordsLambda`.
- Authorization is handled by API Gateway JWT authorizer. The handler reads `sub` (or `username`) from the JWT claims.
- All bodies are JSON with `Content-Type: application/json`.
- Dates are ISO `YYYY-MM-DD` strings.
- `monthKey` is `YYYY-MM`.

## Core endpoints

### Health

- `GET /health`

Response:
```json
{ "status": "ok" }
```

### Create record

- `POST /workspaces/{workspaceId}/records`

Body:
```json
{
  "recordType": "INVOICE",
  "recordId": "optional-custom-id",
  "payload": { /* type-specific payload */ }
}
```

Success response (201):
```json
{
  "workspaceId": "ws-123",
  "recordKey": "INVOICE#2024-06-20#01HZXJ7G5PDT5A1M4M7C2DT9C9",
  "recordId": "01HZXJ7G5PDT5A1M4M7C2DT9C9",
  "recordType": "INVOICE",
  "eventDate": "2024-06-20",
  "payload": { /* original payload as JSON */ },
  "createdAt": "2024-06-21T09:30:00Z",
  "updatedAt": "2024-06-21T09:30:00Z",
  "createdBy": "cognito-sub",
  "updatedBy": "cognito-sub"
}
```

### Get record

- `GET /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`

Success response (200): same shape as create.

### Update record

- `PUT /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`

Body uses the same schema as create. `recordType` must match the path. If `recordId` is provided in the body, it must also match the path.

Success response (200): same shape as create with updated timestamps.

### Delete record

- `DELETE /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`

Success response (204): empty body.

### List records by month

- `GET /workspaces/{workspaceId}/records?month=YYYY-MM`
- Optional filter: `recordType=INVOICE|EXPENSE|STATE_PAYMENT|TRANSFER|BUDGET`

Success response (200):
```json
{ "items": [ /* record responses */ ] }
```

### List records by quarter

- `GET /workspaces/{workspaceId}/records?quarter=YYYY-Q1`
- Optional filter: `recordType=INVOICE|EXPENSE|STATE_PAYMENT|TRANSFER|BUDGET`

Success response (200):
```json
{ "items": [ /* record responses */ ] }
```

## Payload formats

All Money values are JSON numbers. All Percentage values are JSON numbers in `[0,1]`.

### Invoice

```json
{
  "invoiceDate": "2024-06-10",
  "number": "INV-42",
  "client": "Acme",
  "baseExclVat": 1000.00,
  "ivaRate": "STANDARD",
  "retencion": "STANDARD",
  "paymentDate": "2024-06-20",
  "amountReceivedOverride": 1100.00
}
```

### Expense

```json
{
  "documentDate": "2024-06-05",
  "vendor": "Vendor",
  "category": "Software",
  "baseExclVat": 200.00,
  "ivaRate": "REDUCED",
  "vatRecoverableFlag": true,
  "deductibleShare": 0.5,
  "paymentDate": "2024-06-08",
  "amountPaidOverride": 210.00
}
```

### StatePayment

```json
{
  "paymentDate": "2024-07-01",
  "type": "Modelo303",
  "amount": 300.00
}
```

### Transfer

```json
{
  "date": "2024-07-02",
  "operation": "Inflow",
  "amount": 150.00,
  "note": "Owner draw"
}
```

### BudgetEntry

```json
{
  "monthKey": "2024-07",
  "plannedSpend": 2000.00,
  "earned": 2500.00,
  "description": "Summer budget",
  "budgetGoal": "Save for tax"
}
```

## Event date derivation

`eventDate` is derived from the payload:

- Invoice: `paymentDate ?: invoiceDate`
- Expense: `paymentDate ?: documentDate`
- StatePayment: `paymentDate`
- Transfer: `date`
- BudgetEntry: `monthKey.firstDay()` (stored as `YYYY-MM-01`)

For updates, the `eventDate` in the path must match the existing record key.

## Error responses

All errors return JSON with shape:
```json
{ "message": "reason" }
```

Common errors:

- 400: missing params, invalid date/recordType, bad JSON, or payload validation failures.
- 401: missing/invalid JWT authorizer.
- 404: record not found.
- 409: create conflict when `recordKey` already exists.

## API Gateway wiring (HTTP API v2)

The Lambda handler is a single integration with multiple routes. The route keys should match exactly.

Recommended routes:

- `GET /health`
- `POST /workspaces/{workspaceId}/records`
- `GET /workspaces/{workspaceId}/records`
- `GET /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`
- `PUT /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`
- `DELETE /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`

Authorizer:

- Use a JWT authorizer pointing at your Cognito user pool.
- Require it on all routes except `/health` if desired.
- The handler reads `sub` from the JWT claims to set `createdBy`/`updatedBy`.

Integration:

- Lambda proxy integration (payload format 2.0).
- Use handler `autonomo.handler.RecordsLambda`.

## Local usage

You can exercise the controller without Lambda by creating request bodies and calling `RecordsController` methods in Kotlin tests, or by mocking API Gateway v2 events.
