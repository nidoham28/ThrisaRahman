--  user types

do $$
begin
  if to_regtype('public.gender_type') is null then
    create type public.gender_type as enum ('male', 'female', 'other', 'prefer_not_to_say');
  end if;
  if to_regtype('public.account_type') is null then
    create type public.account_type as enum ('user');
  end if;
  if to_regtype('public.account_status_type') is null then
    create type public.account_status_type as enum ('active', 'restricted', 'suspended', 'banned');
  end if;
  if to_regtype('public.language_type') is null then
    create type public.language_type as enum ('Eng', 'Ban');
  end if;
  if to_regtype('public.app_language') is null then
    create type public.app_language as enum ('Eng', 'Ban');
  end if;
  if to_regtype('public.assistant_relationship') is null then
    create type public.assistant_relationship as enum (
      'stranger', 'friend', 'close_friend', 'girlfriend', 'wife'
    );
  end if;
  if to_regtype('public.assistant_voice_type') is null then
    create type public.assistant_voice_type as enum ('female', 'male');
  end if;
  if to_regtype('public.device_type') is null then
    create type public.device_type as enum ('unknown');
  end if;
  if to_regtype('public.os_type') is null then
    create type public.os_type as enum ('Other');
  end if;
  if to_regtype('public.moderation_action_type') is null then
    create type public.moderation_action_type as enum ('warning', 'restrict', 'suspend', 'ban');
  end if;
  if to_regtype('public.speech_engine') is null then
    create type public.speech_engine as enum ('system', 'gemini');
  end if;
  if to_regtype('public.ai_provider') is null then
    create type public.ai_provider as enum ('gemini', 'openrouter');
  end if;
  if to_regtype('public.ui_theme') is null then
    create type public.ui_theme as enum ('system', 'light', 'dark');
  end if;
  if to_regtype('public.font_size') is null then
    create type public.font_size as enum ('small', 'medium', 'large');
  end if;
end
$$;

create or replace function public.array_has_unique_elements(p_values anyarray)
returns boolean
language sql
immutable
strict
parallel safe
set search_path = ''
as $$
  select cardinality(p_values) = (
    select count(*)
    from (select distinct value from unnest(p_values) as items(value)) as unique_values
  )
$$;
