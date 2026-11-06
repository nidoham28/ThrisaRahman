alter table public.user_settings
  add column if not exists active_provider public.ai_provider
  not null default 'gemini'::public.ai_provider;

grant select (active_provider, active_providers, credentials_enc)
  on public.user_settings to authenticated;
grant update (active_provider, active_providers, credentials_enc)
  on public.user_settings to authenticated;

notify pgrst, 'reload schema';
