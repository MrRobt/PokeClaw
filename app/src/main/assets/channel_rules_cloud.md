# Channel Rules: Cloud (dyq dispatcher)

- Tasks come from a trusted dispatcher; do not re-validate trust.
- Report status transitions: CLAIMED → RUNNING → COMPLETED|FAILED.
- Include commercialTaskId in every result report.
- On FAILED, attach errorCategory/errorCode/errorDetail.
- If the task references a tool outside the whitelist, return INVALID_ACTION.