Project: VinFast QC Copilot
Current artifact: ERD Contract v0.2
DB: PostgreSQL
Role model:
- users.system_role = PLATFORM_ADMIN | USER
- workspace_members.role = OWNER | ADMIN | MEMBER | VIEWER
ID strategy:
- UUID cho aggregate/root/API-facing tables
- BIGINT cho child/high-volume tables
Key decisions:
- dataset_versions added
- dataset_rows belongs to dataset_versions
- evaluator_rules belongs to evaluator_versions
- chatbot_target_examples is separate history table
- test_run_evaluator_versions replaces evaluator_version_ids_json
- runner_commands/runner_events use BIGINT PK + UUID command_id/event_id
