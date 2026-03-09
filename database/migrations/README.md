## Migration Plan (MySQL 8.x)

These scripts normalize the current schema in safe phases:

1. `V1__schema_phase1_add_structures.sql`
2. `V2__schema_phase2_backfill_data.sql`
3. `V3__schema_phase3_constraints_and_indexes.sql`
4. `V4__schema_phase4_drop_legacy_columns.sql` (run only after application code no longer uses legacy columns)
5. `V5__schema_add_flow_templates.sql`

### Important

- Take a full DB backup before running.
- Run on MySQL 8.x.
- Execute each file once, in order.
- Validate application behavior between phases.

### Current app compatibility

- V1 to V3 are designed to be additive and compatible with the current app.
- V4 is destructive and must be delayed until app code is switched to:
  - `flow_steps.participant_id` (instead of `participant_name`)
  - `user_roles` (instead of `users.roles`)
