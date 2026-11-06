--  user settings

create table if not exists public.user_settings (
  id uuid not null default gen_random_uuid(),
  uid uuid not null,
  speech_engine public.speech_engine not null default 'system'::public.speech_engine,
  active_providers public.ai_provider[] not null default array[]::public.ai_provider[],
  credentials_enc bytea,
  theme public.ui_theme not null default 'system'::public.ui_theme,
  font_size public.font_size not null default 'medium'::public.font_size,
  language public.app_language not null default 'Ban'::public.app_language,
  stored_history boolean not null default true,
  stored_memories boolean not null default true,
  create_at timestamptz not null default now(),
  update_at timestamptz not null default now(),
  voice_output_enabled boolean not null default true,
  constraint user_settings_pkey primary key (id),
  constraint user_settings_uid_key unique (uid),
  constraint user_settings_uid_fkey foreign key (uid)
    references public.user_profiles (id) on delete cascade,
  constraint chk_settings_active_providers_unique
    check (public.array_has_unique_elements(active_providers))
);

create index if not exists user_settings_active_providers_gin
  on public.user_settings using gin (active_providers);

create or replace function public.user_settings_guard()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'UPDATE' and (
    new.id is distinct from old.id
    or new.uid is distinct from old.uid
    or new.create_at is distinct from old.create_at
  ) then
    raise exception 'Settings identity and creation time are immutable';
  end if;

  if tg_op = 'UPDATE' then
    new.update_at := now();
  end if;
  return new;
end
$$;

drop trigger if exists trg_user_settings_guard on public.user_settings;
create trigger trg_user_settings_guard
  before insert or update on public.user_settings
  for each row execute function public.user_settings_guard();
