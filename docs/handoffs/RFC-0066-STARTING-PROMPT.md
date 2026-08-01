# RFC-0066 Starting Prompt — Deterministic Contract Extraction

Implement deterministic Kotlin/Android source extraction into the RFC-0065 DIR 0.5 Contract model. Use explicit source Evidence; do not infer business meaning from names, use AI, or select the first candidate. Preserve Snapshot format 3, Stable IDs, Feature/Scenario identity, and empty-migration semantics.

Cover all nine roles where deterministic Evidence exists. Ambiguous kind, owner, type, producer/consumer, navigation destination, persistence binding, callback delivery, or external endpoint must produce explicit `UnresolvedItem` references. Do not implement Contract Markdown rendering or Contract artifacts; those belong to RFC-0067.
