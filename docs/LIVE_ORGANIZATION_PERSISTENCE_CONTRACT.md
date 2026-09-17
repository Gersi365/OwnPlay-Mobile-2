# Live Organization Persistence Contract

This document defines the persistence boundary for Provider Categories and OwnPlay Categories. It is a durable product/engineering contract, not a record of current branch status.

## Core rule

Live organization is resolved before visibility and ordering.

```text
source
→ organization mode (PROVIDER | OWNPLAY)
→ category tree
→ category hidden/order within sibling scope
→ channel membership
→ channel hidden/order within membership scope
→ render
```

Category and channel visibility/order are therefore scoped personalization. They are not global Live-channel properties.

Favorites, local channel name, and local logo remain channel-level personalization unless a later explicit product decision changes them.

## Provider organization

Provider-owned category identity, names, order, availability, and channel attribution remain provider metadata.

OwnPlay must not rewrite provider membership to implement the alternate organization mode.

Provider categories may still be hidden and reordered locally. Provider channel memberships may also have independent hidden/order state.

An uncategorized provider channel uses the stable local scope id:

`__provider_uncategorized__`

This is a personalization scope identifier only. It is not a synthetic provider category.

## OwnPlay organization

OwnPlay categories are local organization nodes over real provider channels.

They may form a hierarchy such as:

```text
Italy
├── General
├── Film
└── Sport
    ├── DAZN
    ├── Football
    └── Serie A
```

One real channel may have multiple OwnPlay memberships without duplicating the channel record.

Automatic and manual membership decisions must remain distinguishable. Manual classification decisions have precedence over later automatic classification.

## Required persistence capabilities

The v3 schema design must support these concepts without destructively replacing current provider metadata:

1. Per-source active organization preference: `PROVIDER` or `OWNPLAY`.
2. OwnPlay category nodes with parent/child hierarchy and stable category identity.
3. Category personalization keyed by source + organization mode + category identity.
4. OwnPlay channel memberships with classification origin/evidence and explicit manual inclusion/exclusion support.
5. Channel-membership personalization keyed by source + organization mode + category identity + channel identity.
6. Independent ordering among sibling categories and among channels inside one membership scope.

## Non-destructive v2 → v3 migration design

The existing v2 tables remain intact during the first migration stage.

New organization tables are additive. The migration must not delete or reinterpret provider rows.

Existing Provider-mode personalization is copied forward as follows:

- existing Live `category_personalization.hidden/manualOrder` → Provider-mode category personalization;
- existing `channel_personalization.hidden/manualOrder` → Provider-mode channel-membership personalization for the channel's current provider category;
- uncategorized channels use `__provider_uncategorized__` as the membership scope;
- `favorite`, `localName`, and `localLogo` remain in channel-level personalization;
- existing custom groups remain a separate feature and are not converted to OwnPlay categories.

The legacy hidden/manualOrder columns may remain temporarily for rollback/bridge safety until Provider-mode reads and writes have moved to scoped organization persistence. Removing them is not part of the first migration.

## Proposed additive tables

Final names may change only if implementation evidence shows a simpler normalized shape, but responsibilities must remain separate.

### `live_organization_preferences`

- `sourceId` primary key / foreign key to source
- `activeMode` (`PROVIDER` | `OWNPLAY`)

Default after migration: `PROVIDER`.

### `ownplay_live_categories`

- `sourceId`
- `categoryId`
- `parentCategoryId` nullable
- `displayName`
- `semanticKey` nullable
- `origin` (`AUTO` | `MANUAL`)
- availability / classifier generation metadata as required by reconciliation

### `live_category_scope_personalization`

- `sourceId`
- `organizationMode`
- `categoryId`
- `hidden`
- `manualOrder` nullable

The parent relationship belongs to the category node/provider structure; manual order is interpreted only among siblings.

### `ownplay_live_channel_memberships`

- `sourceId`
- `categoryId`
- `channelId`
- `included`
- `origin` (`AUTO` | `MANUAL`)
- confidence/evidence fields for automatic discovery
- classifier generation/availability metadata as required

An explicit manual exclusion may be persisted as `included = false` so a later classifier refresh cannot silently recreate the membership.

### `live_channel_membership_personalization`

- `sourceId`
- `organizationMode`
- `categoryId`
- `channelId`
- `hidden`
- `manualOrder` nullable

This table is the key requirement that allows the same channel to have different visibility/order in different Provider or OwnPlay contexts.

## Refresh contract

Provider refresh must continue to preserve stable provider identities and last-known-good data during partial failure.

OwnPlay automatic classification may be recomputed only for automatic decisions. Manual memberships, manual exclusions, category personalization, and channel-membership personalization must survive refresh.

No OwnPlay category with zero included channel memberships is rendered.

## Backup boundary

The existing backup format must not be changed as part of the initial organization migration.

Before scoped organization state becomes user-facing and authoritative, backup/restore representation must be reviewed separately. Any breaking backup-format change still requires explicit approval under the project contract.

## UI boundary

This persistence contract does not authorize UI behavior by itself. Provider/OwnPlay switching, discovery review, bulk recategorization, and advanced management are later stages built on this model.
