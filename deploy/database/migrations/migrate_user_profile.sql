-- 账户与用户资料拆分：sys_user 只保留账户字段，资料迁移到 user_profile

BEGIN;

CREATE TABLE IF NOT EXISTS public.user_profile (
    user_id integer NOT NULL,
    name character varying(255) NOT NULL,
    phone character varying(255),
    email character varying(255),
    created_date timestamp(6) with time zone,
    last_modified_date timestamp(6) with time zone,
    created_by_id integer,
    last_modified_by_id integer,
    CONSTRAINT user_profile_pkey PRIMARY KEY (user_id)
);

ALTER TABLE public.user_profile OWNER TO postgres;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_user' AND column_name = 'name'
    ) THEN
        INSERT INTO public.user_profile
            (user_id, name, phone, email, created_date, last_modified_date, created_by_id, last_modified_by_id)
        SELECT id, name, phone, email, created_date, last_modified_date, created_by_id, last_modified_by_id
        FROM public.sys_user
        ON CONFLICT (user_id) DO NOTHING;
    END IF;
END $$;

ALTER TABLE public.sys_user DROP COLUMN IF EXISTS name;
ALTER TABLE public.sys_user DROP COLUMN IF EXISTS phone;
ALTER TABLE public.sys_user DROP COLUMN IF EXISTS email;

COMMIT;
