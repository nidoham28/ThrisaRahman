--  user assistant

create table if not exists public.user_assistant (
  id uuid not null default gen_random_uuid(),
  uid uuid not null,
  name text not null,
  language public.app_language not null default 'Ban'::public.app_language,
  relationship public.assistant_relationship not null default 'stranger'::public.assistant_relationship,
  voice_type public.assistant_voice_type not null default 'female'::public.assistant_voice_type,
  create_at timestamptz not null default now(),
  update_at timestamptz not null default now(),
  constraint user_assistant_pkey primary key (id),
  constraint user_assistant_uid_key unique (uid),
  constraint user_assistant_uid_fkey foreign key (uid)
    references public.user_profiles (id) on delete cascade,
  constraint chk_assistant_name_not_blank check (
    length(btrim(name)) between 1 and 60
  )
);

create index if not exists user_assistant_relationship_idx
  on public.user_assistant using btree (relationship);

create or replace function public.user_assistant_guard()
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
    raise exception 'Assistant identity and creation time are immutable';
  end if;

  new.name := btrim(new.name);
  if tg_op = 'UPDATE' then
    new.update_at := now();
  end if;
  return new;
end
$$;

drop trigger if exists trg_user_assistant_guard on public.user_assistant;
create trigger trg_user_assistant_guard
  before insert or update on public.user_assistant
  for each row execute function public.user_assistant_guard();
