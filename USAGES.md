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
- Optional query: `includeDeleted=true` (includes workspaces in Trash)

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

### Delete workspace

- `DELETE /workspaces/{workspaceId}`

Notes:

- Requires workspace owner access.
- Moves the workspace to Trash for 30 days, after which it will be permanently deleted (DynamoDB TTL).
- While in Trash, the workspace cannot be accessed via `/workspaces/{workspaceId}/...` routes.

Success response (204): empty body.

### Restore workspace

- `POST /workspaces/{workspaceId}/restore`

Notes:

- Requires workspace owner access.
- Restores a workspace from Trash (if it hasn't been permanently deleted yet).

Success response (204): empty body.

### Workspace settings

- `GET /workspaces/{workspaceId}/settings`
- `PUT /workspaces/{workspaceId}/settings`

Settings may include `balanceAccounts`. The default Main account uses
`accountId: "main"`, and old clients may continue sending only
`openingBalance`; when an update omits `balanceAccounts`, the API preserves any
persisted account definitions.

## Regular spendings (recurring expenses)

These endpoints are specialized helpers for “Regular spendings” UI screens.

They are **read-only** and allowed for read-only shared workspace members (membership required).

### List regular spending definitions

- `GET /workspaces/{workspaceId}/regular-spendings`

Success response (200):
```json
{ "items": [ /* RecordResponse with recordType=REGULAR_SPENDING */ ], "nextToken": null }
```

### List regular spending occurrences (date range)

- `GET /workspaces/{workspaceId}/regular-spendings/occurrences?from=YYYY-MM-DD&to=YYYY-MM-DD`

Success response (200):
```json
{
  "from": "2026-02-01",
  "to": "2026-02-28",
  "items": [
    { "recordId": "rec-1", "name": "Gym", "payoutDate": "2026-02-15", "amount": 29.99 }
  ]
}
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
- Optional filter: `recordType=INVOICE|EXPENSE|STATE_PAYMENT|TRANSFER|BUDGET|REGULAR_SPENDING`
- Optional sort: `sort=eventDateDesc`
- Optional pagination: `limit=<1..200>&nextToken=<opaque>` (when `nextToken` is provided, `limit` is required)

Success response (200):
```json
{ "items": [ /* record responses */ ], "nextToken": "optional-opaque" }
```

### List records by quarter

- `GET /workspaces/{workspaceId}/records?quarter=YYYY-Q1`
- Optional filter: `recordType=INVOICE|EXPENSE|STATE_PAYMENT|TRANSFER|BUDGET|REGULAR_SPENDING`
- Optional sort: `sort=eventDateDesc`
- Optional pagination: `limit=<1..200>&nextToken=<opaque>` (when `nextToken` is provided, `limit` is required)

Success response (200):
```json
{ "items": [ /* record responses */ ], "nextToken": "optional-opaque" }
```

### List records by year

- `GET /workspaces/{workspaceId}/records?year=YYYY`
- Optional filter: `recordType=INVOICE|EXPENSE|STATE_PAYMENT|TRANSFER|BUDGET|REGULAR_SPENDING`
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

Quarter summaries include `modelo130DueThisQuarter`, the cumulative filing-oriented Modelo 130 estimate for the quarter. The existing `irpfReserve` remains the period cash-planning reserve.

### IVA year estimate

- `POST /workspaces/{workspaceId}/summaries/iva`

Body: `autonomo.domain.Settings` (see schema in `README.md`).

Success response (200):
```json
{
  "settings": { /* echoed settings */ },
  "iva": { /* IvaYearEstimate */ }
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
  "vatTreatment": "SPANISH_IVA",
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
  "ivaDeductiblePercentage": 1.0,
  "irpfDeductiblePercentage": 0.5,
  "paymentDate": "2024-06-08",
  "amountPaidOverride": 210.00
}
```

Legacy expense payloads can omit `ivaDeductiblePercentage` and `irpfDeductiblePercentage`; in that case `vatRecoverableFlag` maps to full/no IVA deductibility and `deductibleShare` maps to IRPF deductibility. If both legacy and new fields are present, the explicit new percentages are used by core.

### StatePayment

```json
{
  "paymentDate": "2024-07-01",
  "type": "Modelo303",
  "amount": 300.00
}
```

For Modelo 130 payments, `paymentDate` remains the cash/event date. Tax math
uses the filing quarter: send optional `taxPeriodQuarter` (`{"year":2024,"quarter":2}`)
for unusual dates, or omit it and the core will infer normal delayed filing
months (April -> Q1, July -> Q2, October -> Q3, January -> previous year's Q4).

### Transfer

```json
{
  "date": "2024-07-02",
  "operation": "Inflow",
  "amount": 150.00,
  "accountId": "main",
  "note": "Owner draw"
}
```

Legacy external transfer payloads without `accountId` are treated as Main.
Internal account transfers use a distinct discriminator and net to zero at the
total-balance level:

```json
{
  "date": "2026-06-17",
  "movementType": "InternalTransfer",
  "fromAccountId": "main",
  "toAccountId": "cash",
  "amount": 300.00,
  "note": "ATM"
}
```

### BudgetEntry

```json
{
  "monthKey": "2024-07",
  "spent": 2000.00,
  "earned": 2500.00,
  "targetSpend": 1800.00,
  "notes": "Summer budget",
  "exceptionalSpend": 150.00,
  "exceptionalNotes": "One-off travel"
}
```

Compatibility:

- `spent` is the normalized field for new writes.
- Legacy clients may continue sending `plannedSpend`; when both `spent` and `plannedSpend` are present, `spent` wins.
- `earned` remains user-entered and is not derived from summaries.
- Existing payloads with `description` remain readable as legacy notes.
- Existing payloads with `budgetGoal` remain readable for compatibility; new numeric targets should use `targetSpend`.
- `targetSpend` and `exceptionalSpend`, when present, must be `>= 0`.
- `exceptionalSpend`, when present, must be less than or equal to `spent`.
- Only one `BUDGET` record may exist per workspace/month. Duplicate creates return `409 Conflict`.

### RegularSpending (REGULAR_SPENDING)

```json
{
  "name": "Gym",
  "startDate": "2026-02-15",
  "cadence": "MONTHLY",
  "amount": 29.99
}
```

## Event date derivation

`eventDate` is derived from the payload:

- Invoice: `paymentDate ?: invoiceDate`
- Expense: `paymentDate ?: documentDate`
- StatePayment: `paymentDate`
- Transfer: `date`
- BudgetEntry: `monthKey.firstDay()` (stored as `YYYY-MM-01`)
- RegularSpending: `startDate`

For updates, the `eventDate` in the path identifies the existing record key.
For `TRANSFER` updates, changing `date` moves the stored item to the matching
`TRANSFER#<new-date>#<same-record-id>` key while preserving `recordId` and
creation metadata. For BudgetEntry updates, `monthKey` cannot be changed.

## Balance

- `GET /workspaces/{workspaceId}/balance?year=YYYY&accountId=<accountId>`

The endpoint loads persisted settings and all `TRANSFER` records. `year` filters
visible ledger rows only; current balances and running balances include prior
history. `accountId` filters rows to movements touching that account.

Success response (200):

```json
{
  "workspaceId": "ws-123",
  "asOfDate": "2026-06-18",
  "year": 2026,
  "selectedAccountId": "main",
  "totalCurrentBalance": 1500.00,
  "accounts": [
    {
      "accountId": "main",
      "name": "Main",
      "kind": "MAIN",
      "archived": false,
      "closedAt": null,
      "currentBalance": 1200.00
    }
  ],
  "ledgerRows": [
    {
      "recordKey": "TRANSFER#2026-06-17#rec-1",
      "recordId": "rec-1",
      "eventDate": "2026-06-17",
      "movementType": "InternalTransfer",
      "fromAccountId": "main",
      "toAccountId": "cash",
      "amount": 300.00,
      "selectedAccountImpact": -300.00,
      "totalBalanceImpact": 0.00,
      "selectedAccountRunningBalance": 1200.00,
      "totalRunningBalance": 1500.00,
      "note": "ATM"
    }
  ],
  "nextPageToken": null
}
```

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
- 409: create conflict when `recordKey` already exists, or when a `BUDGET` entry already exists for the same workspace/month.

## Workspace membership checks

Before any record CRUD, the handler verifies the caller is a member of the workspace using the `workspace_members` table:

- Primary lookup: `PK = workspace_id`, `SK = USER#<user_id>`
- Fallback lookup (if email is present): `PK = workspace_id`, `SK = EMAIL#<lowercased_email>`
- Allowed statuses: `ACTIVE`, `ACCEPTED`, `OWNER` (or `status` missing)

If no membership is found (or status is not allowed), the API returns `403`.

### Read-only access (shared workspaces)

The API supports read-only sharing via email membership rows:

- Share row: `PK = workspace_id`, `SK = EMAIL#<lowercased_email>`, `role = READER`, `status = ACTIVE`
- Read operations (`GET` list/get records, `POST` summaries, `GET` settings) require membership (`canAccess`).
- Write operations (`POST`/`PUT`/`DELETE` records, `PUT` settings) additionally require a write-capable role (`OWNER`/`MEMBER`).
  - If the workspace is read-only for the caller, the API returns `403` with `"Workspace is read-only for this user"`.

## API Gateway wiring (HTTP API v2)

The Lambda handler is a single integration with multiple routes. The route keys should match exactly.

Required routes (CDK deploys these explicitly; add more when you add new endpoints):

- `GET /health`
- `GET /workspaces`
- `POST /workspaces`
- `GET /workspaces/{workspaceId}/settings`
- `PUT /workspaces/{workspaceId}/settings`
- `GET /workspaces/{workspaceId}/balance`
- `POST /workspaces/{workspaceId}/share`
- `GET /workspaces/{workspaceId}/regular-spendings`
- `GET /workspaces/{workspaceId}/regular-spendings/occurrences`
- `POST /workspaces/{workspaceId}/records`
- `GET /workspaces/{workspaceId}/records`
- `POST /workspaces/{workspaceId}/summaries/months`
- `POST /workspaces/{workspaceId}/summaries/quarters`
- `POST /workspaces/{workspaceId}/summaries/iva`
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
