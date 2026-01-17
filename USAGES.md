# API Usage Guide

This document explains how to call the Lambda-backed REST API and how to wire the routes in API Gateway (HTTP API v2). It assumes Cognito JWT authorizer is used and the `sub` claim is present.

## Base assumptions

- All endpoints are under the same Lambda handler: `autonomo.handler.RecordsLambda`.
- Authorization is handled by API Gateway JWT authorizer. The handler reads `sub` (or `username`) and `email` from the JWT claims.
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

### List workspaces

- `GET /workspaces`

Success response (200):
```json
{
  "items": [
    { "workspaceId": "ws-123", "name": "My workspace", "role": "OWNER", "status": "OWNER" }
  ]
}
```

### Create workspace

- `POST /workspaces`

Body:
```json
{
  "name": "My workspace",
  "settings": { /* autonomo.domain.Settings */ }
}
```

Success response (201):
```json
{
  "workspace": { "workspaceId": "ws-123", "name": "My workspace", "role": "OWNER", "status": "OWNER" },
  "settings": { /* echoed settings */ }
}
```

### Workspace settings

- `GET /workspaces/{workspaceId}/settings`
- `PUT /workspaces/{workspaceId}/settings`

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
- Optional sort: `sort=eventDateDesc`
- Optional pagination: `limit=<1..200>&nextToken=<opaque>` (when `nextToken` is provided, `limit` is required)

Success response (200):
```json
{ "items": [ /* record responses */ ], "nextToken": "optional-opaque" }
```

### List records by quarter

- `GET /workspaces/{workspaceId}/records?quarter=YYYY-Q1`
- Optional filter: `recordType=INVOICE|EXPENSE|STATE_PAYMENT|TRANSFER|BUDGET`
- Optional sort: `sort=eventDateDesc`
- Optional pagination: `limit=<1..200>&nextToken=<opaque>` (when `nextToken` is provided, `limit` is required)

Success response (200):
```json
{ "items": [ /* record responses */ ], "nextToken": "optional-opaque" }
```

## Summaries

Summaries are computed using `autonomo-control-core` and the records stored in the workspace.

### Monthly summaries

- `POST /workspaces/{workspaceId}/summaries/months`

Body: `autonomo.domain.Settings` (see schema in `README.md`).

Success response (200):
```json
{
  "settings": { /* echoed settings */ },
  "items": [ /* MonthSummary */ ]
}
```

### Quarterly summaries

- `POST /workspaces/{workspaceId}/summaries/quarters`

Body: `autonomo.domain.Settings` (see schema in `README.md`).

Success response (200):
```json
{
  "settings": { /* echoed settings */ },
  "items": [ /* QuarterSummary */ ]
}
```

## Payload formats

All Money values are JSON numbers. All rate/percentage values are JSON numbers in `[0,1]`.

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
- 403: user is not a member of the workspace.
- 404: record not found.
- 409: create conflict when `recordKey` already exists.

## Workspace membership checks

Before any record CRUD, the handler verifies the caller is a member of the workspace using the `workspace_members` table:

- Primary lookup: `PK = workspace_id`, `SK = USER#<user_id>`
- Fallback lookup (if email is present): `PK = workspace_id`, `SK = EMAIL#<lowercased_email>`
- Allowed statuses: `ACTIVE`, `ACCEPTED`, `OWNER` (or `status` missing)

If no membership is found (or status is not allowed), the API returns `403`.

## API Gateway wiring (HTTP API v2)

The Lambda handler is a single integration with multiple routes. The route keys should match exactly.

Required routes (CDK deploys these explicitly; add more when you add new endpoints):

- `GET /health`
- `GET /workspaces`
- `POST /workspaces`
- `GET /workspaces/{workspaceId}/settings`
- `PUT /workspaces/{workspaceId}/settings`
- `POST /workspaces/{workspaceId}/records`
- `GET /workspaces/{workspaceId}/records`
- `POST /workspaces/{workspaceId}/summaries/months`
- `POST /workspaces/{workspaceId}/summaries/quarters`
- `GET /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`
- `PUT /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`
- `DELETE /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`

Authorizer:

- Use a JWT authorizer pointing at your Cognito user pool.
- Require it on all routes except `/health` if desired.
- The handler reads `sub` and `email` from the JWT claims to set `createdBy`/`updatedBy` and check membership.

Integration:

- Lambda proxy integration (payload format 2.0).
- Use handler `autonomo.handler.RecordsLambda`.

## Local usage

You can exercise the controller without Lambda by creating request bodies and calling `RecordsController` methods in Kotlin tests, or by mocking API Gateway v2 events.
