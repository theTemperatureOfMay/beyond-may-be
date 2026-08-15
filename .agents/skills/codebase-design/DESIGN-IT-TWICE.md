# Design It Twice

Use this when one design question has materially different answers. The purpose is comparison, not a
mandatory multi-agent ceremony.

## Process

1. State the fixed constraints: existing callers, API/Service/domain names, invariants, errors,
   transactions, dependencies, and the behavior tests must observe.
2. Produce two genuinely different alternatives. Add a third only when it exposes a real trade-off.
   Parallel agents are optional and should be used only when the active workflow explicitly calls for
   them; one agent can perform the comparison.
3. For each alternative, show:
   - the caller-visible methods or HTTP surface;
   - what complexity moves behind that surface;
   - the existing or proposed test seam;
   - migration and compatibility effects;
   - strengths, weaknesses, and failure modes.
4. Compare depth, locality, testability, project convention fit, and implementation risk.
5. Recommend one alternative. Do not write files or code, and do not turn the recommendation into an
   implementation plan unless the user separately requests the appropriate workflow.
