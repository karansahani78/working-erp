package com.educationerp.student;

import com.educationerp.institution.Institution;
import com.educationerp.institution.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates student numbers on the server. Clients never supply a student number, and
 * the format is institution-configurable per the blueprint (for example
 * {@code BCA-2026-00124}).
 *
 * <p>The counter row is created on demand and incremented under a pessimistic write
 * lock, so two concurrent admission approvals cannot receive the same number. The
 * operation joins the caller's transaction so a failed enrolment rolls the counter back
 * with everything else.
 */
@Component
@RequiredArgsConstructor
public class StudentNumberGenerator {

    private static final String DEFAULT_FORMAT = "{PREFIX}-{YEAR}-{SEQ:5}";
    private static final int MAX_ATTEMPTS = 5;

    private static final Pattern TOKEN = Pattern.compile("\\{([A-Z]+)(?::(\\d+))?}");

    private final StudentNumberSequenceRepository sequenceRepository;
    private final StudentNumberSettingRepository settingRepository;
    private final InstitutionRepository institutionRepository;
    private final StudentRepository studentRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public String next() {
        String format = settingRepository.findFirstByActiveTrue()
                .map(StudentNumberSetting::getFormat)
                .filter(f -> f != null && !f.isBlank())
                .orElse(DEFAULT_FORMAT);
        String prefix = prefix();
        String period = String.valueOf(java.time.Year.now().getValue());

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            StudentNumberSequence sequence = sequenceRepository
                    .lockByPrefixAndPeriod(prefix, period)
                    .orElseGet(() -> sequenceRepository.save(
                            newSequence(prefix, period)));
            sequence.setCurrentValue(sequence.getCurrentValue() + 1);
            sequenceRepository.save(sequence);

            long value = sequence.getCurrentValue();
            String candidate = render(format, prefix, period, value);
            if (!studentRepository.existsByStudentNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique student number");
    }

    private StudentNumberSequence newSequence(String prefix, String period) {
        StudentNumberSequence sequence = new StudentNumberSequence();
        sequence.setId(UUID.randomUUID());
        sequence.setPrefix(prefix);
        sequence.setPeriod(period);
        sequence.setCurrentValue(0L);
        return sequence;
    }

    /**
     * Expands {@code {PREFIX}}, {@code {YEAR}} and zero-padded {@code {SEQ:n}} tokens.
     * Unknown tokens are left untouched so a malformed setting is visible rather than
     * silently producing empty segments.
     */
    String render(String format, String prefix, String period, long sequence) {
        Matcher matcher = TOKEN.matcher(format);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = switch (name) {
                case "PREFIX" -> prefix;
                case "YEAR" -> period;
                case "SEQ" -> {
                    int width = matcher.group(2) == null ? 5 : Integer.parseInt(matcher.group(2));
                    yield pad(sequence, width);
                }
                default -> matcher.group(0);
            };
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String pad(long value, int width) {
        String digits = Long.toString(value);
        if (digits.length() >= width) {
            return digits;
        }
        return "0".repeat(width - digits.length()) + digits;
    }

    /** Institution short code, uppercased and stripped of spaces so numbers stay readable. */
    private String prefix() {
        return institutionRepository.findFirstByOrderByCreatedAtAsc()
                .map(Institution::getShortName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT))
                .filter(name -> !name.isBlank())
                .orElse("STU");
    }
}