package com.educationerp.student;

import com.educationerp.auth.user.User;
import com.educationerp.auth.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * A narrow view of the user module, so the student module can ask about account links
 * without spreading direct use of {@link UserRepository} through its own code.
 */
@Component
@RequiredArgsConstructor
public class UserLookup {

    private final UserRepository users;

    /** The student record this sign-in account belongs to, if it is a student's account. */
    public Optional<UUID> studentIdOf(UUID userId) {
        return users.findById(userId).map(User::getStudentId);
    }

    /** The member-of-staff record this sign-in account belongs to, if it is a staff account. */
    public Optional<UUID> employeeIdOf(UUID userId) {
        return users.findById(userId).map(User::getEmployeeId);
    }
}
