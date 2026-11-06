--  user account

create table if not exists public.user_account (
  id uuid not null default gen_random_uuid(),
  uid uuid not null,
  balance numeric(14, 2) not null default 0.00,
  premium boolean not null default false,
  discount numeric(5, 2) not null default 0.00,
  points integer not null default 0,
  create_at timestamptz not null default now(),
  update_at timestamptz not null default now(),
  constraint user_account_pkey primary key (id),
  constraint user_account_uid_key unique (uid),
  constraint user_account_uid_fkey foreign key (uid)
    references public.user_profiles (id) on delete cascade,
  constraint chk_account_balance_non_negative check (balance >= 0),
  constraint chk_account_discount_range check (discount between 0 and 100),
  constraint chk_account_points_non_negative check (points >= 0)
);

create index if not exists user_account_premium_idx
  on public.user_account using btree (uid)
  where premium = true;

create or replace function public.user_account_guard()
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
    raise exception 'Account identity and creation time are immutable';
  end if;

  if tg_op = 'UPDATE' then
    new.update_at := now();
  end if;
  return new;
end
$$;

drop trigger if exists trg_user_account_guard on public.user_account;
create trigger trg_user_account_guard
  before insert or update on public.user_account
  for each row execute function public.user_account_guard();
