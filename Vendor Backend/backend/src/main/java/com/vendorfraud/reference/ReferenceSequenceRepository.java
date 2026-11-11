package com.vendorfraud.reference;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReferenceSequenceRepository extends JpaRepository<ReferenceSequence, String> {

    /** SELECT ... FOR UPDATE: serialises concurrent generators. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ReferenceSequence s where s.sequenceName = :name")
    Optional<ReferenceSequence> findByNameForUpdate(@Param("name") String name);
}
