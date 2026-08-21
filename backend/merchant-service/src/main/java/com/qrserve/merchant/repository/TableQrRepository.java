package com.qrserve.merchant.repository;

import com.qrserve.merchant.entity.TableQrEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TableQrRepository extends JpaRepository<TableQrEntity, Long> {

    /** Resolves any label, ACTIVE or SUPERSEDED — a stale sticker still takes payments. */
    Optional<TableQrEntity> findByTerminalLabel(String terminalLabel);

    Optional<TableQrEntity> findByTableIdAndState(Long tableId, String state);

    Optional<TableQrEntity> findTopByTableIdOrderByVersionDesc(Long tableId);
}
