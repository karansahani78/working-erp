-- An asset can be out to somebody the institution has no record of: a guest lecturer, a
-- contractor, a hired hand. The register keeps their name but has no id column to point at,
-- so the original check, which demanded that an ASSIGNED asset name exactly one holder id,
-- made every external handover fail with a constraint violation.
--
-- What the constraint was actually protecting against is still worth keeping: a row that
-- names a holder while sitting in AVAILABLE is a register that has stopped agreeing with
-- the shelf. So the rule becomes one-directional. Naming a holder means the asset is out;
-- being out does not require a directory id, because the holder may be outside it.

ALTER TABLE assets DROP CONSTRAINT ck_assets_assignment;

ALTER TABLE assets ADD CONSTRAINT ck_assets_assignment CHECK (
    num_nonnulls(assigned_user_id, assigned_employee_id, assigned_student_id) <= 1
    AND (
        num_nonnulls(assigned_user_id, assigned_employee_id, assigned_student_id) = 0
        OR status = 'ASSIGNED'
    )
);
