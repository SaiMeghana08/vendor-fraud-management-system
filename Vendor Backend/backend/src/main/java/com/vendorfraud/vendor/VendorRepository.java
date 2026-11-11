package com.vendorfraud.vendor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    /**
     * The only query that feeds the vendor dropdown. Soft-deleted and
     * inactive vendors are excluded at the repository level, so they
     * cannot leak into the UI through a forgotten filter.
     */
    @Query("""
           select v from Vendor v
            where v.active = true and v.deletedAt is null
              and (:term is null or lower(v.name) like lower(concat('%', :term, '%'))
                   or lower(v.vendorCode) like lower(concat('%', :term, '%')))
            order by v.name asc
           """)
    List<Vendor> searchActive(@Param("term") String term);

    Optional<Vendor> findByIdAndActiveTrueAndDeletedAtIsNull(Long id);

    Optional<Vendor> findByVendorCode(String vendorCode);
}
