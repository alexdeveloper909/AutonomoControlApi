# AutonomoControlApi

Kotlin serverless API for workspace-scoped financial records (Invoice, Expense, StatePayment, Transfer, BudgetEntry). The code is layered so Lambda handlers are thin and business logic is testable.

## License

This repository is **source-available** for transparency and review, but it is **proprietary** and **not open source**. See `LICENSE`.

## Architecture

- `autonomo/handler` - AWS Lambda entrypoints (API Gateway v2 events)
- `autonomo/controller` - HTTP parsing + status codes
- `autonomo/service` - business logic, record key derivation
- `autonomo/repository` - DynamoDB persistence
- `autonomo-control-core` - domain types + monthly/quarterly summary calculations

## DynamoDB tables

This service expects the existing `workspace_records` table described in the spec:

- PK: `workspace_id`
- SK: `record_key` = `<TYPE>#<event_date>#<record_id>`
- GSI `by_month`: PK `workspace_month`, SK `record_key`
- GSI `by_quarter`: PK `workspace_quarter`, SK `record_key`

Stored attributes per record include:
`record_id`, `record_type`, `event_date`, `payload_json`, `workspace_month`, `workspace_quarter`, `created_at`, `updated_at`, `created_by`, `updated_by`.

Membership checks use the `workspace_members` table before allowing CRUD. The handler looks up `USER#<user_id>` and optionally `EMAIL#<lowercased_email>` for the workspace.

Payload conventions:
- Money fields are JSON numbers (e.g., `baseExclVat: 1000.00`).
- Rate/percentage fields are JSON numbers in `[0,1]` (e.g., `deductibleShare: 0.5`).
- Dates are `YYYY-MM-DD` strings; `monthKey` is `YYYY-MM`.

## Endpoints (API Gateway v2)

All endpoints expect a valid Cognito JWT authorizer. The handler reads `sub` from JWT claims.

- `GET /health`
- `POST /workspaces/{workspaceId}/summaries/months`
- `POST /workspaces/{workspaceId}/summaries/quarters`
- `POST /workspaces/{workspaceId}/records`
  - body:
    ```json
    {
      "recordType": "INVOICE",
      "recordId": "optional-custom-id",
      "payload": {
        "invoiceDate": "2024-06-10",
        "number": "INV-42",
        "client": "Acme",
        "baseExclVat": 1000.00,
        "ivaRate": "STANDARD",
        "retencion": "STANDARD",
        "paymentDate": "2024-06-20",
        "amountReceivedOverride": 1100.00
      }
    }
    ```
- `GET /workspaces/{workspaceId}/records?month=YYYY-MM&recordType=INVOICE`
- `GET /workspaces/{workspaceId}/records?quarter=YYYY-Q1&recordType=EXPENSE`
- `GET /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`
- `PUT /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`
  - body uses the same schema as create; `recordType` must match the path
- `DELETE /workspaces/{workspaceId}/records/{recordType}/{eventDate}/{recordId}`

### Summaries payload

Summaries endpoints accept `autonomo.domain.Settings` (from `autonomo-control-core`) as the request body:

```json
{
  "year": 2025,
  "startDate": "2025-08-01",
  "ivaStd": 0.21,
  "irpfRate": 0.20,
  "obligacion130": true,
  "openingBalance": 200.00,
  "expenseCategories": ["Software/SaaS", "Equipment", "Other"]
}
```

Responses include `items` with `monthKey` (`YYYY-MM`) or `quarterKey` (`{ "year": 2025, "quarter": 3 }`) plus calculated totals (cash flow, VAT/IRPF estimates, reserves).

`eventDate` is derived by type:
- Invoice: `paymentDate ?: invoiceDate`
- Expense: `paymentDate ?: documentDate`
- StatePayment: `paymentDate`
- Transfer: `date`
- BudgetEntry: `monthKey.firstDay()`

## Configuration

Environment variables:

- `ENV` (default `local`; use `dev` / `prod` in deployed stages)
- `DDB_TABLE_PREFIX` / `DYNAMODB_TABLE_PREFIX` / `AUTONOMO_TABLE_PREFIX` (optional; see below)
- `WORKSPACE_RECORDS_TABLE` (optional; default `workspace_records` for local)
- `WORKSPACE_MEMBERS_TABLE` (optional; default `workspace_members` for local)
- `WORKSPACE_SETTINGS_TABLE` (optional; default `workspace_settings` for local)
- `WORKSPACES_TABLE` (optional; default `workspaces` for local)
- `USERS_TABLE` (optional; default `users` for local)
- `DYNAMODB_ENDPOINT` (default `http://localhost:8000` for local DynamoDB)
- `AWS_REGION` or `AWS_DEFAULT_REGION` (default `eu-west-1`)

### Dev/prod table naming

Recommended: set a single prefix via `DDB_TABLE_PREFIX` and let the service derive all table names:

- `${DDB_TABLE_PREFIX}-users`
- `${DDB_TABLE_PREFIX}-workspaces`
- `${DDB_TABLE_PREFIX}-workspace_settings`
- `${DDB_TABLE_PREFIX}-workspace_members`
- `${DDB_TABLE_PREFIX}-workspace_records`

If `ENV` is `dev` or `prod` and no prefix is provided, the default prefix is `autonomo-control-$ENV` (e.g. `autonomo-control-dev`).

You can also override any table name explicitly via the per-table environment variables listed above (these take precedence over the prefix).

## Local development

- Use DynamoDB Local and set `ENV=local` and `DYNAMODB_ENDPOINT`.
- `RecordsLambda` can be invoked by API Gateway or locally by constructing an `APIGatewayV2HTTPEvent` in tests.

## Build

```bash
./gradlew build
```

## GitHub Packages dependency

This project consumes `com.alex:autonomo-control-core:1.0.0` from GitHub Packages.

Add credentials locally (do not commit) in `~/.gradle/gradle.properties`:

```properties
github.owner=YOUR_GITHUB_USERNAME_OR_ORG
github.repo=YOUR_REPO_NAME
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

Alternatively, set environment variables:

```bash
export GITHUB_OWNER=YOUR_GITHUB_USERNAME_OR_ORG
export GITHUB_REPO=YOUR_REPO_NAME
export GITHUB_ACTOR=YOUR_GITHUB_USERNAME
export GITHUB_TOKEN=YOUR_GITHUB_TOKEN
```

Token scopes: `read:packages` (and `repo` for private repos).

## Tests

Unit tests cover controller validation and payload date derivation. Run them with:

```bash
./gradlew test
```

## Lambda handler

Use `autonomo.handler.RecordsLambda` as the handler entrypoint in CDK.
