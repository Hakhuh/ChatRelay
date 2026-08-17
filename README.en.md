![Purpur/Paper](https://img.shields.io/badge/Purpur%2FPaper-1.21%2B-blueviolet)
![Java](https://img.shields.io/badge/Java-21-orange)

[Русская версия](README.md)

# ChatRelay

A Minecraft plugin for Purpur or Paper servers. It buffers player chat in memory and hands it out through the `/chatpull` command when a Telegram bot asks for it. Its job is to let the bot show in-game chat straight in Telegram.

## Why it exists

The bot sends Telegram messages to the server with a plain `tellraw` over RCON. That part needs no plugin. But to pull player messages back into Telegram, the server needs something that collects them and returns them on request. That is what ChatRelay does.

The bot runs fine without the plugin. Server chat just will not reach Telegram.

## How it works

- Every chat message is caught through `AsyncChatEvent` and stored in a buffer: nick and text, with all color codes already stripped.
- Every 10 seconds the bot calls `/chatpull` over RCON. The command drains the buffer and returns it as one block, each line looking like `[Nick]: message`.
- A message lives for 15 seconds. If nobody pulls it, it is dropped. A prune task runs every 5 seconds, so the buffer never grows without bound, even if the bot goes down.
- No open ports, no HTTP. Everything goes through the same RCON channel already set up for the bot.

## Requirements

- Purpur or Paper 1.21+. Written and running on Purpur, which is a fork of Paper.
- Java 21.
- RCON enabled on the server.

## Installation

1. Download `ChatRelay-1.0.0.jar` from the Releases section.
2. Drop it into the server's `plugins/` folder.
3. Restart the server.
4. Make sure RCON is enabled in `server.properties`: `enable-rcon=true`, with `rcon.port` and `rcon.password` set.

The `chatrelay.use` permission defaults to operator. The RCON console runs with operator rights, so the command works with no extra setup.

## Building from source

If you want to build it yourself:

```bash
mvn package
```

The jar will appear at `target/ChatRelay-1.0.0.jar`.

## Command

`/chatpull` drains the buffered messages and returns them. It is meant for the bot and is called over RCON. There is no point running it by hand.

## Bot

Works together with The Abyss bot. Repository: https://github.com/Hakhuh/the-abyss-bot
