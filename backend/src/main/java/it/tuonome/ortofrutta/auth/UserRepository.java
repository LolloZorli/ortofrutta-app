package it.tuonome.ortofrutta.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

}
