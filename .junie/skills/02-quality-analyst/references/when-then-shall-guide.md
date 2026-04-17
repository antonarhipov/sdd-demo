# WHEN-THEN-SHALL Format Guide

## Structure
- WHEN: precondition or trigger (the state of the world)
- THEN: the action or event that occurs
- SHALL: the observable outcome that must be true

## Good Examples

WHEN a CSV file contains a row with a missing required field
THEN the import process encounters that row
SHALL skip the row, log a warning with the row number and
field name, and continue processing remaining rows

WHEN a user submits valid login credentials
THEN the authentication endpoint processes the request
SHALL return HTTP 200 with a session token that expires
in 24 hours

## Bad Examples (and why)

❌ "SHALL store data in PostgreSQL using JDBC"
→ Prescribes implementation. Better: "SHALL persist data such
that it survives application restart"

❌ "WHEN the system starts, SHALL load all configurations
AND initialize the cache AND connect to the database"
→ Compound criterion, not independently testable.
Split into three separate criteria.

❌ "SHALL work correctly"
→ Not observable or testable. What does "correctly" mean?

❌ "WHEN the user clicks the blue submit button on the
bottom-right of the form"
→ Prescribes UI implementation. Better: "WHEN the user
submits the registration form"

## Boundary Criteria Pattern
For any value with limits, write three criteria:
- Within bounds (happy path)
- At the boundary exactly
- Beyond the boundary (error case)

## Error Criteria Pattern
Always specify:
- What the system SHALL do (not just what it won't do)
- Whether the operation is atomic (all-or-nothing)
  or partial (process what you can, report failures)
