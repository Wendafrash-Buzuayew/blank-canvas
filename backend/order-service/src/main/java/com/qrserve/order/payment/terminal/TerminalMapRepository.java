package com.qrserve.order.payment.terminal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalMapRepository extends JpaRepository<TerminalMapEntity, String> {
}
