--  user session

create table if not exists public.user_session (
  id uuid not null default gen_random_uuid(),
  uid uuid not null,
  session_token_hash text not null,
  device_name text,
  device_model text,
  device_type public.device_type not null default 'unknown'::public.device_type,
  operating_system public.os_type not null default 'Other'::public.os_type,
  app_version text,
  ip_address inet,
  user_agent text,
  last_active_at timestamptz not null default now(),
  expired_at timestamptz not null,
  revoked_at timestamptz,
  create_at timestamptz not null default now(),
  constraint user_session_pkey primary key (id),
  constraint user_session_uid_fkey foreign key (uid)
    references public.user_profiles (id) on delete cascade,
  constraint chk_session_expired_after_create check (expired_at > create_at),
  constraint chk_session_revoked_after_create check (
    revoked_at is null or revoked_at >= create_at
  ),
  constraint chk_session_token_hash_not_blank check (length(session_token_hash) > 0)
);

create unique index if not exists user_session_token_hash_uidx
  on public.user_session using btree (session_token_hash);
create index if not exists user_session_uid_idx
  on public.user_session using btree (uid);
create index if not exists user_session_active_idx
  on public.user_session using btree (uid)
  where revoked_at is null;
create index if not exists user_session_cleanup_idx
  on public.user_session using btree (expired_at)
  where revoked_at is null;

create or replace function public.user_session_immutable_fields()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.id is distinct from old.id
    or new.uid is distinct from old.uid
    or new.session_token_hash is distinct from old.session_token_hash
    or new.create_at is distinct from old.create_at
  then
    raise exception 'Session identity, token hash, and creation time are immutable';
  end if;
  return new;
end
$$;

drop trigger if exists trg_user_session_immutable on public.user_session;
create trigger trg_user_session_immutable
  before update on public.user_session
  for each row execute function public.user_session_immutable_fields();
