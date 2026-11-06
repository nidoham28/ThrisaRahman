--  user access policies

alter table public.user_profiles enable row level security;
alter table public.user_assistant enable row level security;
alter table public.user_settings enable row level security;
alter table public.user_session enable row level security;
alter table public.user_moderation enable row level security;
alter table public.user_account enable row level security;

drop policy if exists user_profiles_select_own on public.user_profiles;
create policy user_profiles_select_own on public.user_profiles
  for select to authenticated
  using (id = (select auth.uid()));
drop policy if exists user_profiles_update_own on public.user_profiles;
create policy user_profiles_update_own on public.user_profiles
  for update to authenticated
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));

drop policy if exists user_assistant_select_own on public.user_assistant;
create policy user_assistant_select_own on public.user_assistant
  for select to authenticated
  using (uid = (select auth.uid()));
drop policy if exists user_assistant_update_own on public.user_assistant;
create policy user_assistant_update_own on public.user_assistant
  for update to authenticated
  using (uid = (select auth.uid()))
  with check (uid = (select auth.uid()));

drop policy if exists user_settings_select_own on public.user_settings;
create policy user_settings_select_own on public.user_settings
  for select to authenticated
  using (uid = (select auth.uid()));
drop policy if exists user_settings_update_own on public.user_settings;
create policy user_settings_update_own on public.user_settings
  for update to authenticated
  using (uid = (select auth.uid()))
  with check (uid = (select auth.uid()));

revoke all on public.user_profiles, public.user_assistant, public.user_settings,
  public.user_session, public.user_moderation, public.user_account
  from anon, authenticated;

grant select (id, username, display_name, phone, gender)
  on public.user_profiles to authenticated;
grant update (username, display_name, phone, gender)
  on public.user_profiles to authenticated;

grant select (uid, name, language, relationship, voice_type)
  on public.user_assistant to authenticated;
grant update (name, language, relationship, voice_type)
  on public.user_assistant to authenticated;

grant usage on type public.gender_type, public.account_type,
  public.account_status_type, public.language_type, public.app_language,
  public.assistant_relationship, public.assistant_voice_type, public.device_type,
  public.os_type, public.moderation_action_type, public.speech_engine,
  public.ai_provider, public.ui_theme, public.font_size to authenticated;
grant usage on schema public to authenticated;

grant select (uid, speech_engine, theme, font_size, language,
  stored_history, stored_memories, voice_output_enabled)
  on public.user_settings to authenticated;
grant update (speech_engine, theme, font_size, language,
  stored_history, stored_memories, voice_output_enabled)
  on public.user_settings to authenticated;

notify pgrst, 'reload schema';
