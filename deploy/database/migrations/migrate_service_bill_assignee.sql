-- 服务单指派：由单人字段升级为独立子表，支持一张单据指派给多人
--
-- 兼容旧版本：如果数据库里还残留 service_bill.assignee_id，会把旧负责人复制到子表后删除该列。
-- 新建数据库直接使用 init_schema.sql 中的 service_bill_assignee，本脚本可重复执行。

BEGIN;

CREATE SEQUENCE IF NOT EXISTS public.service_bill_assignee_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE IF NOT EXISTS public.service_bill_assignee (
    id integer DEFAULT nextval('public.service_bill_assignee_seq'::regclass) NOT NULL,
    service_bill_id integer NOT NULL,
    user_id integer NOT NULL,
    created_date timestamp(6) with time zone,
    last_modified_date timestamp(6) with time zone,
    created_by_id integer,
    last_modified_by_id integer,
    CONSTRAINT service_bill_assignee_pkey PRIMARY KEY (id),
    CONSTRAINT uk_service_bill_assignee UNIQUE (service_bill_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_service_bill_assignee_bill_id
    ON public.service_bill_assignee USING btree (service_bill_id);
CREATE INDEX IF NOT EXISTS idx_service_bill_assignee_user_id
    ON public.service_bill_assignee USING btree (user_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'service_bill'
          AND column_name = 'assignee_id'
    ) THEN
        INSERT INTO public.service_bill_assignee
            (service_bill_id, user_id, created_date, last_modified_date, created_by_id, last_modified_by_id)
        SELECT id, assignee_id, now(), now(), created_by_id, created_by_id
        FROM public.service_bill
        WHERE assignee_id IS NOT NULL
        ON CONFLICT (service_bill_id, user_id) DO NOTHING;

        ALTER TABLE public.service_bill DROP COLUMN assignee_id;
    END IF;
END $$;

DROP INDEX IF EXISTS public.idx_service_bill_assignee_id;

COMMIT;
