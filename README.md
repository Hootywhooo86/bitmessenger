# bitmessenger

Talk, but chunkier. The 128bit family's pixel messaging app: chunky chat
bubbles, pixel stickers, family group chats and chat streaks — with the
shared 128bit family feed woven right into the conversation. Part of the
128bit family.

> Product direction is Daniel's call — this README is a starter brief so
> Carlos (Claude Code) has something concrete to react to. The landing page
> is live; the app itself is unstarted.

## Concept (starter — Daniel decides)

A messenger with two jobs:

1. **Be a genuinely good chat app.** 1:1 and group chats in full pixel
   style: chunky bubbles, pixel avatars and status lines, typing
   indicators, read receipts, photo sharing, pixel sticker packs. Fast,
   reliable delivery first — a cute messenger that drops messages is a
   dead messenger.
2. **Be where the family feed lives.** The shared 128bit event feed
   posts into the family group chat like it happened there: quest
   completed, trip booked, streak saved. Chat streaks and group quests
   emit events back into the feed — 128bitlife issues the quests,
   bitmessenger is where the family actually talks about them.

## Open questions for Daniel

- **The big call — what carries the messages?**
  (a) Standalone network: 128bit accounts messaging each other
  (needs servers, push infra, and everyone you know to install it —
  the classic messenger cold-start problem);
  (b) SMS/RCS client: on Android it can be the default texting app,
  pixel skin over real texts to anyone (iOS can't do this);
  (c) Bridge: pixel client over an existing open protocol (e.g.
  Matrix) so it talks to a wider world from day one.
  Recommendation: (b) for Android reach + family-group layer on 128bit
  accounts for the feed/streak features — texts reach everyone, the
  magic happens in the family group.
- **Encryption:** end-to-end by default (Signal protocol or Matrix's
  Olm/Megolm) or ship simple and add it later? Family chats argue for
  E2EE from day one; it shapes the whole backend choice.
- **How gamified is chat, really?** Streaks + sticker-pack rewards are
  in the concept. Read-receipt XP? Message-count leaderboards? Where's
  the line between playful and annoying — Daniel draws it.
- **bitcall tie-in:** voice/video calls live here (messenger-style) or
  stay in bitcall (dialer-style)? Recommendation: texts here, calls in
  bitcall, one-tap jump between them.

## Architecture — real chat, pixel skin

```
src/
  chat/        # Conversation list, threads, composer. Virtualized
               # lists — long histories must scroll like butter.
  transport/   # The message pipe. Backend choice follows Daniel's
               # call above (SMS/RCS, Matrix bridge, or 128bit
               # accounts). One interface, swappable pipe.
  crypto/      # End-to-end encryption if Daniel calls E2EE (likely):
               # key storage on-device, safety numbers, no plaintext
               # on any server we run. Never roll our own crypto.
  stickers/    # Pixel sticker packs: bundled, earned via 128bitlife
               # quests, tradable. Pack format shared with the family.
  familyfeed/  # Renders shared-feed events as system messages in the
               # family group ("🏆 Quest completed: Morning Miles").
               # Display + post-back of chat events only.
  events.ts    # 128bit shared event schema — emit message.sent,
               # streak.day, group.created, sticker.earned, etc.
               # 128bitlife subscribes for quests + XP.
```

Rules:
- **Delivery is sacred.** Messages queue offline, send in order, and
  never silently vanish. Retry with backoff; show honest states
  (sending / sent / delivered / read).
- **Private by default.** No ads, no message-content analytics, no
  contact-list uploads without explicit opt-in. If E2EE is the call,
  servers see ciphertext, period.
- **Gamify the connection, not the compulsion.** Streaks reward
  talking to your people, not staring at the app. No engagement traps
  in a family messenger.
- **Local-first history.** Chat history lives on-device; backup is
  encrypted and opt-in.
- Never store raw credentials in the repo.

## Start-here brief for Claude

```
We're building bitmessenger — the 128bit family's pixel messaging
app: "talk, but chunkier." Read this README first.

Build order:
1. Transport call (ask Daniel): SMS/RCS client, Matrix bridge, or
   standalone 128bit accounts? And is E2EE day-one? Both answers
   shape everything — don't scaffold past them.
2. src/chat/ — conversation list + one thread with the pixel bubble
   UI, honest message states, offline queue. Against a local fake
   transport first.
3. src/transport/ — wire the chosen pipe behind the interface from
   step 2. Prove delivery + ordering under airplane mode.
4. src/crypto/ — if E2EE: integrate the established library for the
   chosen protocol. Never hand-roll crypto.
5. src/events.ts — shared 128bit event schema; emit message.sent,
   streak.day. (Check sibling repos for the schema stub.)
6. src/familyfeed/ — shared-feed events rendered as system messages
   in the family group; src/stickers/ — first bundled pixel pack.

Constraints: delivery reliability above features, private by default,
gamify connection not compulsion. Small commits. Never commit
credentials.
```

## 128bit tie-in

bitmessenger is the family's living room: `message.sent`, `streak.day`,
and `sticker.earned` events flow into the shared feed so 128bitlife can
issue chat quests and XP, and the feed flows back — family milestones
from every other 128bit app land in the family group as messages.
bitcall handles calls; one tap jumps between them. Don't silo the data.

## Monetization (family default)

128bit Pro bundle covers bitmessenger. Core messaging — chats, groups,
the family feed — is free forever. Cosmetic IAPs fit naturally here:
premium pixel sticker packs, chat themes and bubble skins, like the
rest of the family. Never paywall encryption or message history.
