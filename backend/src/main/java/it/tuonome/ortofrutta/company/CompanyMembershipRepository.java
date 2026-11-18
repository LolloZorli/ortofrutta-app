package it.tuonome.ortofrutta.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, Long> {

    @Query("select m from CompanyMembership m join fetch m.user join fetch m.role "
            + "where m.companyId = :companyId and lower(m.user.username) = lower(:username) and m.enabled = true")
    Optional<CompanyMembership> findActiveByCompanyAndUsername(@Param("companyId") Long companyId,
                                                                @Param("username") String username);

    @Query("select m from CompanyMembership m join fetch m.user join fetch m.role "
            + "where m.companyId = :companyId order by m.user.username")
    List<CompanyMembership> findAllByCompanyId(@Param("companyId") Long companyId);

    @Query("select m from CompanyMembership m join fetch m.role "
            + "where m.companyId = :companyId and m.user.id = :userId and m.enabled = true")
    Optional<CompanyMembership> findActiveByCompanyAndUserId(@Param("companyId") Long companyId,
                                                             @Param("userId") Long userId);

    @Query("select m from CompanyMembership m join fetch m.user join fetch m.role "
            + "where m.companyId = :companyId and m.user.id = :userId")
    Optional<CompanyMembership> findByCompanyAndUserId(@Param("companyId") Long companyId,
                                                       @Param("userId") Long userId);

    boolean existsByCompanyIdAndUserId(Long companyId, Long userId);

    boolean existsByCompanyIdAndCustomerId(Long companyId, Long customerId);

    long countByCompanyIdAndRoleId(Long companyId, Long roleId);

    long countByUserId(Long userId);
}
