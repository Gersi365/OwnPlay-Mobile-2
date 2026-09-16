# Live Organization Backup Boundary

Status: design-only Stage 6 checkpoint. This document does not change the current backup codec, Room schema, or restore behavior.

## Current authority

The active backup contract remains `ownplay-backup` version 1. `BackupCodec.VERSION` must remain `1` until a separately approved backup-format change is implemented. Current v1 export/import does not include the Room v3 Live organization tables.

Stage 6 therefore defines the representation boundary only. It intentionally stops before any codec or restore mutation.

## User intent that a future backup version should preserve

A future explicitly approved backup version should preserve user-authored Live organization state per source:

- active organization preference: `PROVIDER` or `OWNPLAY`;
- OwnPlay categories protected by manual edits, including stable `categoryId`, parent relationship, display name, and semantic key;
- manual OwnPlay channel-membership overrides: `categoryId`, stable `channelId`, `included`, and deterministic manual evidence keys;
- scoped category personalization: organization mode, category identity, hidden state, and manual sibling order;
- scoped channel-membership personalization: organization mode, category identity, stable channel identity, hidden state, and manual order within that membership.

Manual evidence keys are part of user intent when they encode decisions such as normal-channel confirmation, section-marker confirmation, replace-membership locking, or subtree exclusion. They must never contain credentials, provider URLs, or secret source locators.

## Derived state that should not become backup authority

Automatic classifier output is derived from provider data and deterministic discovery policy. A future backup format should not treat AUTO categories or AUTO memberships as authoritative user data by default. They can be regenerated after a complete successful provider refresh.

This keeps backup semantics focused on user intent and avoids restoring stale classifier output over newer provider evidence.

## Proposed restore ordering

A future version should restore in this order:

1. Restore source records and safe source settings using the existing credential-excluding contract.
2. Refresh the provider catalog when credentials/connectivity are available.
3. Recompute automatic OwnPlay discovery only after an authoritative successful refresh.
4. Reapply manual OwnPlay categories and manual membership overrides.
5. Reapply scoped category and channel-membership personalization.
6. Restore the active organization mode only when its referenced organization is available; otherwise retain Provider mode until reconciliation can complete.

Stable source/category/channel identities must be validated before each scoped row is applied. Missing provider identities should use a bounded pending-restore mechanism rather than synthesizing channels or categories.

## Compatibility requirements for a future version

If a new backup version is approved:

- existing v1 imports must remain supported;
- export must not switch away from v1 until the new representation and downgrade/compatibility behavior are explicitly approved;
- Provider-mode scoped personalization must reconcile with the existing legacy bridge without double-applying conflicting hidden/order values;
- channel-level favorites, local names, and local logos remain independent channel personalization, not membership personalization;
- restore must remain atomic where the current restore contract is atomic;
- unknown or unavailable source/channel identities must not be destructively guessed.

## Approval boundary

Implementing any new backup section, changing `BackupCodec.VERSION`, changing the serialized contract, or changing pending-restore format is outside this Stage 6 source checkpoint and requires explicit approval.
