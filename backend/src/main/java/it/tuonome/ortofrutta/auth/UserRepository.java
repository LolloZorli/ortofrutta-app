package it.tuonome.ortofrutta.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Query("select u from AppUser u join fetch u.role where lower(u.email) = lower(:email)")
    Optional<AppUser> findWithRoleByEmailIgnoreCase(@Param("email") String email);

    /** Utente con il suo ruolo in una sola query (i permessi del ruolo si caricano subito). */
    @Query("select u from AppUser u join fetch u.role where u.username = :username")
    Optional<AppUser> findWithRoleByUsername(@Param("username") String username);

    @Query("select u from AppUser u join fetch u.role order by u.username")
    List<AppUser> findAllWithRole();

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    long countByRoleId(Long roleId);
}
