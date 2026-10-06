package com.educationerp.library;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Builds the smallest library a test needs: a category, a book, a copy and a member.
 *
 * <p>Rows are seeded through JDBC rather than the API so that a test can assert on the
 * behaviour under test without every test first walking the whole catalogue wizard.
 */
@Component
public class TestLibraryFixture {

    private final JdbcTemplate jdbcTemplate;

    public TestLibraryFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Returns the new book's id. */
    public UUID book(String categoryCode, String title) {
        UUID category = category(categoryCode);
        return jdbcTemplate.queryForObject("""
                insert into books (id, title, is_reference, created_at, updated_at)
                values (gen_random_uuid(), ?, false, now(), now())
                returning id
                """, UUID.class, title);
    }

    /** Returns the new copy's id. */
    public UUID copy(UUID bookId) {
        return jdbcTemplate.queryForObject("""
                insert into book_copies (id, book_id, barcode, status, condition_status,
                                         acquisition_type, created_at, updated_at)
                values (gen_random_uuid(), ?, ?, 'AVAILABLE', 'GOOD', 'PURCHASE', now(), now())
                returning id
                """, UUID.class, bookId, "BC-TEST-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /** Returns the new member's id. */
    public UUID member(String name) {
        return jdbcTemplate.queryForObject("""
                insert into library_members (id, member_code, external_name, max_books, status,
                                            membership_start, created_at, updated_at)
                values (gen_random_uuid(), ?, ?, 3, 'ACTIVE', current_date, now(), now())
                returning id
                """, UUID.class, "M-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), name);
    }

    /**
     * A card that belongs to a signed-in user, which is how a student, employee or parent
     * borrows. It is the case that matters: lending used to ask for circulation rights only
     * when somebody named a member, so omitting it lent the book on the caller's own card to
     * anyone signed in with a library card.
     */
    public UUID memberForUser(UUID userId) {
        return jdbcTemplate.queryForObject("""
                insert into library_members (id, member_code, user_id, max_books, status,
                                            membership_start, created_at, updated_at)
                values (gen_random_uuid(), ?, ?, 3, 'ACTIVE', current_date, now(), now())
                returning id
                """, UUID.class, "M-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(), userId);
    }

    public String copyStatus(UUID copyId) {
        return jdbcTemplate.queryForObject(
                "select status from book_copies where id = ?", String.class, copyId);
    }

    private UUID category(String code) {
        var existing = jdbcTemplate.query(
                "select id from library_categories where code = ?", (rs, i) -> rs.getObject(1), code);
        if (!existing.isEmpty()) {
            return (UUID) existing.get(0);
        }
        return jdbcTemplate.queryForObject("""
                insert into library_categories (id, code, name, created_at, updated_at)
                values (gen_random_uuid(), ?, ?, now(), now())
                returning id
                """, UUID.class, code, code);
    }
}