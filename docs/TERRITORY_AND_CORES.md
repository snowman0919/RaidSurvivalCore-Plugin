# Territory And Cores

Tribe cores are persistent objects stored in `tribe_cores`. A core has a world UUID, block coordinates, tier, health, state, timestamps, and a protection radius.

The runtime territory lookup uses an immutable chunk-indexed snapshot:

```text
Location -> world UUID -> chunk key -> candidate cores -> exact radius check
```

This avoids scanning every core on movement and block events. The index is rebuilt when the core snapshot is loaded or a core is created.

Random spawn checks the active core snapshot and rejects candidate positions near cores. Proximity penalties ignore players in the same tribe.

The plugin does not auto-delete mismatched world blocks. If a core block is missing but the database row exists, use administrative recovery to recreate the block or mark the core disabled after inspection.
