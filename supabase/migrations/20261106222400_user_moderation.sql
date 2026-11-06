--  user moderation

create table if not exists public.user_moderation (
  id uuid not null default gen_random_uuid(),
  uid uuid not null,
  action_type public.moderation_action_type not null,
  reason text not null,
  modified_by uuid not null,
  started_time timestamptz not null default now(),
  expired_at timestamptz,
  revoked_at timestamptz,
  create_at timestamptz not null default now(),
  constraint user_moderation_pkey primary key (id),
  constraint user_moderation_uid_key unique (uid),
  constraint user_moderation_uid_fkey foreign key (uid)
    references public.user_profiles (id) on delete cascade,
  constraint chk_mod_expired_after_start check (
    expired_at is null or expired_at > started_time
  ),
  constraint chk_mod_reason_not_blank check (length(btrim(reason)) > 0),
  constraint chk_mod_revoked_after_start check (
    revoked_at is null or revoked_at >= started_time
  )
);

create index if not exists user_moderation_action_type_idx
  on public.user_moderation using btree (action_type);
create index if not exists user_moderation_active_idx
  on public.user_moderation using btree (uid)
  where revoked_at is null;

create or replace function public.user_moderation_immutable_fields()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.id is distinct from old.id
    or new.uid is distinct from old.uid
    or new.action_type is distinct from old.action_type
    or new.reason is distinct from old.reason
    or new.modified_by is distinct from old.modified_by
    or new.started_time is distinct from old.started_time
    or new.create_at is distinct from old.create_at
  then
    raise exception 'Moderation identity and action details are immutable';
  end if;
  return new;
end
$$;

create or replace function public.sync_account_status_from_moderation()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  target_uid uuid;
  target_status public.account_status_type;
begin
  if tg_op = 'DELETE' then
    target_uid := old.uid;
    target_status := 'active'::public.account_status_type;
  else
    target_uid := new.uid;
    if new.revoked_at is not null
      or (new.expired_at is not null and new.expired_at <= now())
    then
      target_status := 'active'::public.account_status_type;
    else
      target_status := case new.action_type
        when 'restrict' then 'restricted'::public.account_status_type
        when 'suspend' then 'suspended'::public.account_status_type
        when 'ban' then 'banned'::public.account_status_type
        else 'active'::public.account_status_type
      end;
    end if;
  end if;

  update public.user_profiles
  set account_status = target_status
  where id = target_uid
    and account_status is distinct from target_status;

  if tg_op = 'DELETE' then
    return old;
  end if;
  return new;
end
$$;

drop trigger if exists trg_sync_account_status on public.user_moderation;
create trigger trg_sync_account_status
  after insert or delete or update on public.user_moderation
  for each row execute function public.sync_account_status_from_moderation();

drop trigger if exists trg_user_moderation_immutable on public.user_moderation;
create trigger trg_user_moderation_immutable
  before update on public.user_moderation
  for each row execute function public.user_moderation_immutable_fields();
