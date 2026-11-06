-- Seed each new Auth user with their profile and related default rows.
create or replace function public.create_user_data_after_signup()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  signup_at timestamptz := now();
  profile_username text;
  profile_display_name text;
  profile_gender public.gender_type;
  profile_language public.language_type;
begin
  profile_username := nullif(btrim(new.raw_user_meta_data ->> 'username'), '');
  if profile_username is null then
    profile_username := 'user_' || replace(new.id::text, '-', '');
  end if;

  profile_display_name := coalesce(
    nullif(btrim(new.raw_user_meta_data ->> 'display_name'), ''),
    nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
    'User'
  );

  profile_gender := case lower(btrim(coalesce(new.raw_user_meta_data ->> 'gender', '')))
    when 'male' then 'male'::public.gender_type
    when 'female' then 'female'::public.gender_type
    when 'other' then 'other'::public.gender_type
    when 'prefer_not_to_say' then 'prefer_not_to_say'::public.gender_type
    else null
  end;

  profile_language := case upper(btrim(coalesce(new.raw_user_meta_data ->> 'language', '')))
    when 'ENG' then 'Eng'::public.language_type
    when 'EN' then 'Eng'::public.language_type
    when 'ENGLISH' then 'Eng'::public.language_type
    else 'Ban'::public.language_type
  end;

  insert into public.user_profiles (
    id,
    username,
    display_name,
    email,
    phone,
    biography,
    verified,
    date_of_birth,
    gender,
    avatar_url,
    account_type,
    create_at,
    update_at,
    location,
    language,
    account_status,
    account_private,
    email_public,
    phone_public,
    date_of_birth_public,
    gender_public,
    location_public,
    language_public,
    biography_public
  )
  values (
    new.id,
    profile_username,
    profile_display_name,
    new.email,
    new.phone,
    null,
    new.email_confirmed_at is not null or new.phone_confirmed_at is not null,
    null,
    profile_gender,
    null,
    'user'::public.account_type,
    signup_at,
    signup_at,
    null,
    profile_language,
    'active'::public.account_status_type,
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    true
  )
  on conflict (id) do nothing;

  insert into public.user_settings (
    id,
    uid,
    speech_engine,
    active_providers,
    credentials_enc,
    theme,
    font_size,
    language,
    stored_history,
    stored_memories,
    create_at,
    update_at,
    voice_output_enabled
  )
  values (
    gen_random_uuid(),
    new.id,
    'system'::public.speech_engine,
    array[]::public.ai_provider[],
    null,
    'system'::public.ui_theme,
    'medium'::public.font_size,
    case profile_language
      when 'Eng'::public.language_type then 'Eng'::public.app_language
      else 'Ban'::public.app_language
    end,
    true,
    true,
    signup_at,
    signup_at,
    true
  )
  on conflict (uid) do nothing;

  insert into public.user_assistant (
    id,
    uid,
    name,
    language,
    relationship,
    voice_type,
    create_at,
    update_at
  )
  values (
    gen_random_uuid(),
    new.id,
    left(coalesce(nullif(btrim(new.raw_user_meta_data ->> 'assistant_name'), ''), 'Assistant'), 60),
    case profile_language
      when 'Eng'::public.language_type then 'Eng'::public.app_language
      else 'Ban'::public.app_language
    end,
    'stranger'::public.assistant_relationship,
    'female'::public.assistant_voice_type,
    signup_at,
    signup_at
  )
  on conflict (uid) do nothing;

  insert into public.user_account (
    id,
    uid,
    balance,
    premium,
    discount,
    points,
    create_at,
    update_at
  )
  values (
    gen_random_uuid(),
    new.id,
    0.00,
    false,
    0.00,
    0,
    signup_at,
    signup_at
  )
  on conflict (uid) do nothing;

  return new;
end
$$;

revoke all on function public.create_user_data_after_signup()
  from public, anon, authenticated;

drop trigger if exists trg_create_profile_for_auth_user on auth.users;
drop trigger if exists trg_handle_new_user_profile on public.user_profiles;
drop function if exists public.create_profile_for_auth_user();
drop function if exists public.handle_new_user_profile();

drop trigger if exists trg_create_user_data_after_signup on auth.users;
create trigger trg_create_user_data_after_signup
  after insert on auth.users
  for each row execute function public.create_user_data_after_signup();
