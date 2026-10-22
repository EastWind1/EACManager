--
-- PostgreSQL database dump
--

\restrict mDpb7JZnpBMuek1Qv0aWWMahEwIZtKVGcMagKAQotF0NXKR1nBAMRcqhkdeveoB

-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: attachment_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.attachment_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.attachment_seq OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: attachment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.attachment (
    id integer DEFAULT nextval('public.attachment_seq'::regclass) NOT NULL,
    name character varying(255),
    relative_path character varying(255),
    type smallint,
    created_date timestamp(6) with time zone,
    last_modified_date timestamp(6) with time zone,
    created_by_id integer,
    last_modified_by_id integer,
    status smallint NOT NULL,
    CONSTRAINT attachment_status_check CHECK (((status >= 0) AND (status <= 2))),
    CONSTRAINT attachment_type_check CHECK (((type >= 0) AND (type <= 4)))
);


ALTER TABLE public.attachment OWNER TO postgres;

--
-- Name: company_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.company_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.company_seq OWNER TO postgres;

--
-- Name: company; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.company (
    id integer DEFAULT nextval('public.company_seq'::regclass) NOT NULL,
    address character varying(255),
    contact_name character varying(255),
    contact_phone character varying(255),
    name character varying(255),
    created_by_id integer,
    created_date timestamp(6) with time zone,
    last_modified_by_id integer,
    last_modified_date timestamp(6) with time zone,
    email character varying(255),
    disabled boolean
);


ALTER TABLE public.company OWNER TO postgres;

--
-- Name: reimburse_attach_relation_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.reimburse_attach_relation_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.reimburse_attach_relation_seq OWNER TO postgres;

--
-- Name: reimburse_attach_relation; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.reimburse_attach_relation (
    id integer DEFAULT nextval('public.reimburse_attach_relation_seq'::regclass) NOT NULL,
    reimburse_id integer,
    attach_id integer
);


ALTER TABLE public.reimburse_attach_relation OWNER TO postgres;

--
-- Name: reimburse_detail_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.reimburse_detail_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.reimburse_detail_seq OWNER TO postgres;

--
-- Name: reimburse_detail; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.reimburse_detail (
    id integer DEFAULT nextval('public.reimburse_detail_seq'::regclass) NOT NULL,
    amount numeric(38,2),
    name character varying(255),
    reimbursement_id integer
);


ALTER TABLE public.reimburse_detail OWNER TO postgres;

--
-- Name: reimbursement_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.reimbursement_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.reimbursement_seq OWNER TO postgres;

--
-- Name: reimbursement; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.reimbursement (
    id integer DEFAULT nextval('public.reimbursement_seq'::regclass) NOT NULL,
    created_date timestamp(6) with time zone,
    last_modified_date timestamp(6) with time zone,
    number character varying(255),
    reimburse_date timestamp(6) with time zone,
    remark character varying(1000),
    summary character varying(255),
    total_amount numeric(38,2),
    created_by_id integer,
    last_modified_by_id integer,
    state smallint,
    version integer,
    CONSTRAINT reimbursement_state_check CHECK (((state >= 0) AND (state <= 2)))
);


ALTER TABLE public.reimbursement OWNER TO postgres;

--
-- Name: service_bill_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.service_bill_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.service_bill_seq OWNER TO postgres;

--
-- Name: service_bill; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.service_bill (
    id integer DEFAULT nextval('public.service_bill_seq'::regclass) NOT NULL,
    created_date timestamp(6) with time zone,
    last_modified_date timestamp(6) with time zone,
    elevator_info character varying(255),
    number character varying(255),
    on_site_contact character varying(255),
    on_site_phone character varying(255),
    processed_date timestamp(6) with time zone,
    project_address character varying(255),
    project_contact character varying(255),
    project_contact_phone character varying(255),
    project_name character varying(255),
    remark character varying(1000),
    state smallint,
    total_amount numeric(38,2),
    type smallint,
    created_by_id integer,
    last_modified_by_id integer,
    order_date timestamp(6) with time zone,
    finished_date timestamp(6) with time zone,
    product_company_id integer,
    version integer,
    CONSTRAINT service_bill_state_check CHECK (((state >= 0) AND (state <= 3))),
    CONSTRAINT service_bill_type_check CHECK (((type >= 0) AND (type <= 1)))
);


ALTER TABLE public.service_bill OWNER TO postgres;

--
-- Name: service_bill_attach_relation_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.service_bill_attach_relation_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.service_bill_attach_relation_seq OWNER TO postgres;

--
-- Name: service_bill_attach_relation; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.service_bill_attach_relation (
    id integer DEFAULT nextval('public.service_bill_attach_relation_seq'::regclass) NOT NULL,
    service_bill_id integer,
    attach_id integer
);


ALTER TABLE public.service_bill_attach_relation OWNER TO postgres;

--
-- Name: service_bill_detail_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.service_bill_detail_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.service_bill_detail_seq OWNER TO postgres;

--
-- Name: service_bill_detail; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.service_bill_detail (
    id integer DEFAULT nextval('public.service_bill_detail_seq'::regclass) NOT NULL,
    device character varying(255),
    quantity numeric(38,2),
    remark character varying(255),
    subtotal numeric(38,2),
    unit_price numeric(38,2),
    service_bill_id integer
);


ALTER TABLE public.service_bill_detail OWNER TO postgres;

--
-- Name: sys_user_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.sys_user_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.sys_user_seq OWNER TO postgres;

--
-- Name: sys_user; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.sys_user (
    id integer DEFAULT nextval('public.sys_user_seq'::regclass) NOT NULL,
    created_date timestamp(6) with time zone,
    last_modified_date timestamp(6) with time zone,
    email character varying(255),
    disabled boolean NOT NULL,
    name character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    phone character varying(255),
    username text,
    created_by_id integer,
    last_modified_by_id integer,
    authority character varying(255),
    CONSTRAINT user_authority_check CHECK (((authority)::text = ANY (ARRAY[('ROLE_ADMIN'::character varying)::text, ('ROLE_USER'::character varying)::text, ('ROLE_GUEST'::character varying)::text, ('ROLE_FINANCE'::character varying)::text])))
);


ALTER TABLE public.sys_user OWNER TO postgres;

--
-- Name: attachment attachment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attachment
    ADD CONSTRAINT attachment_pkey PRIMARY KEY (id);


--
-- Name: company company_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.company
    ADD CONSTRAINT company_pkey PRIMARY KEY (id);


--
-- Name: reimburse_attach_relation reimburse_attach_relation_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.reimburse_attach_relation
    ADD CONSTRAINT reimburse_attach_relation_pkey PRIMARY KEY (id);


--
-- Name: reimburse_detail reimburse_detail_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.reimburse_detail
    ADD CONSTRAINT reimburse_detail_pkey PRIMARY KEY (id);


--
-- Name: reimbursement reimbursement_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.reimbursement
    ADD CONSTRAINT reimbursement_pkey PRIMARY KEY (id);


--
-- Name: service_bill_attach_relation service_bill_attach_relation_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service_bill_attach_relation
    ADD CONSTRAINT service_bill_attach_relation_pkey PRIMARY KEY (id);


--
-- Name: service_bill_detail service_bill_detail_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service_bill_detail
    ADD CONSTRAINT service_bill_detail_pkey PRIMARY KEY (id);


--
-- Name: service_bill service_bill_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.service_bill
    ADD CONSTRAINT service_bill_pkey PRIMARY KEY (id);


--
-- Name: sys_user uk_user_username; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sys_user
    ADD CONSTRAINT uk_user_username UNIQUE (username);


--
-- Name: sys_user user_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.sys_user
    ADD CONSTRAINT user_pkey PRIMARY KEY (id);


--
-- Name: idx_attachment_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_attachment_status ON public.attachment USING btree (status);


--
-- Name: idx_reimburse_attach_relation_bill_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_reimburse_attach_relation_bill_id ON public.reimburse_attach_relation USING btree (reimburse_id);


--
-- Name: idx_reimbursement_number; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_reimbursement_number ON public.reimbursement USING btree (number);


--
-- Name: idx_reimbursement_reimburse_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_reimbursement_reimburse_date ON public.reimbursement USING btree (reimburse_date);


--
-- Name: idx_service_bill_attach_relation_bill_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_service_bill_attach_relation_bill_id ON public.service_bill_attach_relation USING btree (service_bill_id);


--
-- Name: idx_service_bill_created_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_service_bill_created_date ON public.service_bill USING btree (created_date);


--
-- Name: idx_service_bill_number; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_service_bill_number ON public.service_bill USING btree (number);


--
-- Name: idx_user_username; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_user_username ON public.sys_user USING btree (username);


--
-- PostgreSQL database dump complete
--

\unrestrict mDpb7JZnpBMuek1Qv0aWWMahEwIZtKVGcMagKAQotF0NXKR1nBAMRcqhkdeveoB

