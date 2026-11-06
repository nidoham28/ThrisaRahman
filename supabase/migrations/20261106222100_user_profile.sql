--  user profile

create table if not exists public.user_profiles (
  id uuid not null default gen_random_uuid()
    references auth.users (id) on delete cascade,
  username text not null,
  display_name text,
  email text,
  phone text,
  biography text,
  verified boolean not null default false,
  date_of_birth date,
  gender public.gender_type,
  avatar_url text,
  account_type public.account_type not null default 'user'::public.account_type,
  create_at timestamptz not null default now(),
  update_at timestamptz not null default now(),
  location text,
  language public.language_type not null default 'Ban'::public.language_type,
  account_status public.account_status_type not null default 'active'::public.account_status_type,
  account_private boolean not null default false,
  email_public boolean not null default false,
  phone_public boolean not null default false,
  date_of_birth_public boolean not null default false,
  gender_public boolean not null default false,
  location_public boolean not null default false,
  language_public boolean not null default false,
  biography_public boolean not null default true,
  constraint user_profiles_pkey primary key (id)
);

create unique index if not exists user_profiles_username_lower_uidx
  on public.user_profiles using btree (lower(username));
create unique index if not exists user_profiles_email_uidx
  on public.user_profiles using btree (lower(email))
  where email is not null;
create unique index if not exists user_profiles_phone_uidx
  on public.user_profiles using btree (phone)
  where phone is not null;
create unique index if not exists user_profiles_avatar_url_uidx
  on public.user_profiles using btree (avatar_url)
  where avatar_url is not null;

create or replace function public.set_user_profile_update_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.update_at := now();
  return new;
end
$$;

create or replace function public.handle_new_user_profile()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.user_settings (uid)
  values (new.id)
  on conflict (uid) do nothing;

  insert into public.user_assistant (uid, name)
  values (new.id, 'Assistant')
  on conflict (uid) do nothing;

  insert into public.user_account (uid)
  values (new.id)
  on conflict (uid) do nothing;

  return new;
end
$$;

create or replace function public.create_profile_for_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  profile_username text;
begin
  profile_username := nullif(btrim(new.raw_user_meta_data ->> 'username'), '');
  if profile_username is null then
    profile_username := 'user_' || left(replace(new.id::text, '-', ''), 12);
  end if;

  insert into public.user_profiles (id, username, display_name, email)
  values (
    new.id,
    profile_username,
    nullif(btrim(new.raw_user_meta_data ->> 'display_name'), ''),
    new.email
  )
  on conflict (id) do nothing;

  return new;
end
$$;

drop trigger if exists trg_create_profile_for_auth_user on auth.users;
create trigger trg_create_profile_for_auth_user
  after insert on auth.users
  for each row execute function public.create_profile_for_auth_user();

drop trigger if exists trg_handle_new_user_profile on public.user_profiles;
create trigger trg_handle_new_user_profile
  after insert on public.user_profiles
  for each row execute function public.handle_new_user_profile();

drop trigger if exists trg_user_profiles_update_at on public.user_profiles;
create trigger trg_user_profiles_update_at
  before update on public.user_profiles
  for each row execute function public.set_user_profile_update_at();
