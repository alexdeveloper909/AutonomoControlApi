# AutonomoControlApi

Kotlin serverless API for workspace-scoped financial records (Invoice, Expense, StatePayment, Transfer, BudgetEntry). The code is layered so Lambda handlers are thin and business logic is testable.

## Architecture

- `autonomo/handler` - AWS Lambda entrypoints (API Gateway v2 events)
- `autonomo/controller` - HTTP parsing + status codes
- `autonomo/service` - business logic, record key derivation
- `autonomo/repository` - DynamoDB persistence
- `autonomo/domain` - model definitions and value objects

## DynamoDB tables

This service expects the existing `workspace_records` table described in the spec:

- PK: `workspace_id`
- SK: `record_key` = `<TYPE>#<event_date>#<record_id>`
- GSI `by_month`: PK `workspace_month`, SK `record_key`
- GSI `by_quarter`: PK `workspace_quarter`, SK `record_key`

Stored attributes per record include:
`record_id`, `record_type`, `event_date`, `payload_json`, `workspace_month`, `workspace_quarter`, `created_at`, `updated_at`, `created_by`, `updated_by`.

Payload conventions:
- Money fields are JSON numbers (e.g., `baseExclVat: 1000.00`).
- Percentage fields are JSON numbers in `[0,1]` (e.g., `deductibleShare: 0.5`).
- Dates are `YYYY-MM-DD` strings; `monthKey` is `YYYY-MM`.

## Endpoints (API Gateway v2)

All endpoints expect a valid Cognito JWT authorizer. The handler reads `sub` from JWT claims.

- `GET /health`
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

`eventDate` is derived by type:
- Invoice: `paymentDate ?: invoiceDate`
- Expense: `paymentDate ?: documentDate`
- StatePayment: `paymentDate`
- Transfer: `date`
- BudgetEntry: `monthKey.firstDay()`

## Configuration

Environment variables:

- `ENV` (default `local`)
- `WORKSPACE_RECORDS_TABLE` (default `workspace_records`)
- `DYNAMODB_ENDPOINT` (default `http://localhost:8000` for local DynamoDB)
- `AWS_REGION` or `AWS_DEFAULT_REGION` (default `eu-west-1`)

## Local development

- Use DynamoDB Local and set `ENV=local` and `DYNAMODB_ENDPOINT`.
- `RecordsLambda` can be invoked by API Gateway or locally by constructing an `APIGatewayV2HTTPEvent` in tests.

## Build

```bash
./gradlew build
```

## Tests

Unit tests cover controller validation and payload date derivation. Run them with:

```bash
./gradlew test
```

## Lambda handler

Use `autonomo.handler.RecordsLambda` as the handler entrypoint in CDK.
