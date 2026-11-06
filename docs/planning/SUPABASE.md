NOTICE: AI only update app  module. other module completely locked. AI use read and call functions inside app module.

# Supabase user schema

The executable schema and access rules are maintained in these migrations:

- [`20261106222000_user_types.sql`](../../supabase/migrations/20261106222000_user_types.sql) defines the enums and shared array helper.
- [`20261106222100_user_profile.sql`](../../supabase/migrations/20261106222100_user_profile.sql) creates user profiles and the profile update-time trigger.
- [`20261106222200_user_assistant.sql`](../../supabase/migrations/20261106222200_user_assistant.sql) creates assistant profiles.
- [`20261106222300_user_session.sql`](../../supabase/migrations/20261106222300_user_session.sql) creates session storage.
- [`20261106222400_user_moderation.sql`](../../supabase/migrations/20261106222400_user_moderation.sql) creates moderation records and account-status synchronization.
- [`20261106222500_user_account.sql`](../../supabase/migrations/20261106222500_user_account.sql) creates account balances and points.
- [`20261106222600_user_settings.sql`](../../supabase/migrations/20261106222600_user_settings.sql) creates user settings.
- [`20261106222700_user_access_policies.sql`](../../supabase/migrations/20261106222700_user_access_policies.sql) enables row-level security and grants owner-scoped access.
- [`20261106222800_use_user_settings_table.sql`](../../supabase/migrations/20261106222800_use_user_settings_table.sql) removes the legacy `my_settings` view; the app reads and writes `public.user_settings`.
- [`20261106225200_user_signup_trigger.sql`](../../supabase/migrations/20261106225200_user_signup_trigger.sql) creates profile, settings, assistant, and account rows with explicit defaults for each new Supabase Auth user.
- [`20261106225300_provider_credentials_vault.sql`](../../supabase/migrations/20261106225300_provider_credentials_vault.sql) stores the selected provider and grants owner-scoped access to encrypted provider data.

Provider vault access is limited to the authenticated user's `user_settings` row by RLS. The client can read and write `credentials_enc`, but that column contains only client-side ciphertext, never plaintext API keys.

Provider API keys are encrypted on-device with AES-GCM using a key derived from the user's 8+ digit PIN. The PIN is never stored or sent to Supabase. Ciphertext is bound to the authenticated user ID, and the PIN is required again on each device. An 8-digit PIN is still susceptible to offline guessing if ciphertext is stolen; use a longer PIN where possible. If the PIN is forgotten, the keys cannot be recovered; replace them by entering new keys after clearing the unusable vault.

## Schema assumptions

- Enum labels follow the SQL defaults and the values currently used by the app. Extend the enums through a later migration before persisting additional values.
- A new Supabase Auth user gets profile, settings, assistant, and account rows in one signup trigger. Session rows are intentionally not fabricated; Supabase Auth owns session tokens. Moderation rows are only created when an actual moderation action is applied.
- Moderation actions `restrict`, `suspend`, and `ban` map to the corresponding account status. Expiry is applied when a moderation row changes; time-based expiry without a row update requires a scheduled database task.
