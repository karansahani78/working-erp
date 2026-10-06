-- Phase 11: staged spreadsheet imports.
--
-- A batch is the record of one spreadsheet from upload to confirmation. The rows are parsed,
-- validated and previewed before anything is written; the mapping says which column fed which
-- field, so a second run with the same file does not have to be guessed at again.
create table import_batches (
    id uuid primary key,
    batch_number varchar(40) not null,
    import_type varchar(30) not null,
    original_filename varchar(255) not null,
    storage_key varchar(400) not null,
    status varchar(20) not null,
    column_mapping jsonb not null default '{}'::jsonb,
    total_rows integer not null default 0,
    valid_rows integer not null default 0,
    invalid_rows integer not null default 0,
    imported_rows integer not null default 0,
    row_errors jsonb not null default '[]'::jsonb,
    import_report jsonb not null default '{}'::jsonb,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0
);

create unique index import_batches_batch_number_key on import_batches (batch_number);
create index import_batches_status_idx on import_batches (status);
create index import_batches_type_idx on import_batches (import_type);

-- The row numbers refer to the spreadsheet as the person sees it: row 1 is the header, so the
-- first data row is row 2. That is the number they will quote when they say "row 42 is wrong",
-- and matching it here means the error message and their spreadsheet agree.
create table import_batch_duplicates (
    id uuid primary key,
    batch_id uuid not null references import_batches (id) on delete cascade,
    row_number integer not null,
    field_name varchar(80) not null,
    existing_value varchar(255) not null,
    incoming_value varchar(255) not null,
    created_at timestamptz not null default now()
);

create index import_batch_duplicates_batch_idx on import_batch_duplicates (batch_id);