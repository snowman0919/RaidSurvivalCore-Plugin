# Chat Obfuscation

General chat uses Paper `AsyncChatEvent` and a per-viewer renderer. Deprecated `AsyncPlayerChatEvent` is not used.

The listener does not query SQLite from the async chat thread. It reads only the immutable `TribeSnapshot`.

Rules:

- Same tribe: plaintext.
- Sender: plaintext.
- `raidcore.chat.spy`: plaintext.
- Console: plaintext log.
- Other players: deterministic obfuscation per viewer and message ID.

Player input is extracted as plain text and placed into new Adventure Components. MiniMessage is not parsed from player chat input. Control and zero-width characters are removed before rendering. URLs, IP-like text, and coordinates are not preserved because letters, digits, slash, colon, and dot are obfuscated.

`/tribe chat` toggles tribe chat mode. `/tc <message>` sends a direct tribe chat message to tribe members and spy viewers.
