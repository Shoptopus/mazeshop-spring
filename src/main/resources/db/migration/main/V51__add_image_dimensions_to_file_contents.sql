alter table file_contents
    add column if not exists width integer,
    add column if not exists height integer;
