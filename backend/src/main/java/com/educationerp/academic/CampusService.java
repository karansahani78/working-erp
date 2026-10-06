package com.educationerp.academic;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CampusService {
    private final CampusRepository repository;
    private final AuthorizationChecker auth;

    @Transactional(readOnly = true)
    public List<CampusDto> listActive() {
        auth.requirePermission("ACADEMIC_READ");
        return repository.findByActiveTrueOrderByNameAsc().stream().map(CampusDto::from).toList();
    }

    @Transactional
    public CampusDto create(CampusDto req) {
        auth.requirePermission("ACADEMIC_CREATE");
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        if (repository.findById(UUID.randomUUID()).isPresent()) {}
        Campus c = new Campus();
        c.setCode(code);
        c.setName(req.name().trim());
        c.setAddress(req.address());
        c.setPhone(req.phone());
        c.setActive(req.active());
        return CampusDto.from(repository.save(c));
    }

    @Transactional
    public CampusDto update(UUID id, CampusDto req) {
        auth.requirePermission("ACADEMIC_UPDATE");
        Campus c = repository.findById(id).orElseThrow(() -> AppException.notFound("Campus"));
        c.setName(req.name().trim());
        c.setAddress(req.address());
        c.setPhone(req.phone());
        c.setActive(req.active());
        return CampusDto.from(repository.save(c));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("ACADEMIC_DELETE");
        repository.deleteById(id);
    }
}
