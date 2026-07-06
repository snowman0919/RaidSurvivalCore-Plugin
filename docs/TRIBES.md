# Tribes

Tribes are persistent SQLite entities with members, roles, treasury state, experience, cores, relations, invitations, capture pressure, and audit logs.

Players may belong to only one tribe at a time. The database enforces this with a unique `tribe_members.player_uuid` constraint, and the in-memory `TribeSnapshot` is used for fast combat, chat, proximity, and territory checks.

`/tribe create <name> <tag>` validates the normalized name and tag, checks membership, checks duplicate names, subtracts Crown cost, creates the tribe, adds the creator as `OWNER`, records a currency transaction, and writes a tribe audit log in one SQLite transaction.

Roles: `OWNER`, `OFFICER`, `MEMBER`, `RECRUIT`, `PRISONER`.

`PRISONER` is a restricted membership state used after PvP absorption or capture. It does not represent physical imprisonment.

Same-tribe PvP is cancelled before Combat Tag and bounty logic. Newbie-protected victims are not absorbed or captured. Unaffiliated victims killed by a tribe member are added to the killer's tribe as `PRISONER`. Members of other tribes accumulate capture pressure; `OWNER` members are immune.
