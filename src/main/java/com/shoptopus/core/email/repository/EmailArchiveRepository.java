package com.shoptopus.core.email.repository;

import com.shoptopus.core.email.EmailArchive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailArchiveRepository extends JpaRepository<EmailArchive, Long> {
}
