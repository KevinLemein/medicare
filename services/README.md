# Services

Each subdirectory here is an independently deployable Spring Boot microservice.
Each service owns its own database — no service reaches into another service's
tables directly (see `docs/architecture/overview.md`).

| Service | Owns | Status |
|---|---|---|
| `identity-service` | Users, credentials, roles, activation/reset tokens, JWT issuance | Skeleton created |
| `patient-service` | Patient demographic/administrative records, patient numbering | Not started |
| `appointment-service` | Doctor schedules, appointment slots, booking, statuses | Not started |
| `clinical-service` | Consultations, medical history, vitals, triage records | Not started |
| `pharmacy-service` | Drug inventory, prescriptions, dispensing | Not started |
| `laboratory-service` | Lab orders, results | Not started |
| `billing-service` | Charges, invoices, payments | Not started |
| `notification-service` | In-app / email / SMS notifications | Not started |
| `audit-service` | Immutable audit trail | Not started |

Exact service boundaries beyond `identity-service` are not finalized — several
(e.g. whether audit is its own service or a shared library + append-only
table per service) are open questions to resolve when we get there.

New services get added to the `<modules>` list in this directory's `pom.xml`
as they're started.
