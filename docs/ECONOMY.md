# Economy

The internal currency is Crown by default. Balances are integer `long` values.

Accounts are stored in `currency_accounts`:

- `player:<uuid>`
- `tribe:<id>`

Every transfer writes `currency_transactions` with an actor, source, target, amount, reason, tax, and timestamp. Administrative adjustments are also written to this table.

Implemented reason codes: `QUEST_REWARD`, `MOB_REWARD`, `SHOP_SELL`, `SHOP_BUY`, `PLAYER_TRANSFER`, `TRIBE_DEPOSIT`, `TRIBE_WITHDRAW`, `TRIBE_CREATION`, `CORE_CREATION`, `CORE_REPAIR`, `CORE_LOOT`, `WAR_DECLARATION`, `BOUNTY_CREATE`, `BOUNTY_REWARD`, `ADMIN_ADJUSTMENT`, `TAX`.

Overflow and insufficient-balance checks happen before balance writes. Multi-step transfers run in SQLite transactions.
