package com.wildme.wildbook_lite.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.wildme.wildbook_lite.config.JpaConfig;
import com.wildme.wildbook_lite.support.AbstractPostgresIT;

/**
 * Repository "slice" test against a real Postgres in a Testcontainer.
 *
 * Spring Boot testing bits:
 *
 *  - @DataJpaTest is a *test slice*: it boots only the JPA stack (entity
 *    manager, repositories, transactions) — NOT controllers, security,
 *    or our custom @Components. Much faster than full @SpringBootTest.
 *
 *  - @AutoConfigureTestDatabase(replace = NONE)
 *      Default @DataJpaTest tries to swap in H2 ("an embedded DB"). We
 *      want the *real* Postgres our base class brought up. NONE = leave
 *      the configured datasource alone.
 *
 *  - @Import(JpaConfig.class)
 *      @DataJpaTest doesn't load arbitrary @Configuration beans. We
 *      need JpaConfig because that's where @EnableJpaAuditing lives
 *      (without it, @CreatedDate on BaseEntity stays null and our
 *      NOT NULL columns blow up).
 *
 *  - @DataJpaTest wraps each test in a transaction that ROLLS BACK at
 *    the end → tests are isolated, no leftover data.
 *
 *  - @ActiveProfiles("test") picks application-test.yml.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(JpaConfig.class)
class UserRepositoryIT extends AbstractPostgresIT {

    @Autowired UserRepository userRepository;

    @Test
    @DisplayName("save then findByUsername round-trips")
    void roundtrip() {
        User u = new User("alice", "alice@x.com", "HASH");
        userRepository.save(u);

        assertThat(userRepository.findByUsername("alice"))
            .isPresent()
            .get()
            .satisfies(found -> {
                assertThat(found.getEmail()).isEqualTo("alice@x.com");
                assertThat(found.getRoles()).containsExactly(Role.RESEARCHER);
                assertThat(found.getCreatedAt()).isNotNull(); // JPA auditing populated it
            });
    }

    @Test
    @DisplayName("unique constraint on username is enforced by Postgres")
    void uniqueUsername() {
        userRepository.saveAndFlush(new User("alice", "a@x.com", "H1"));

        assertThatThrownBy(() ->
            userRepository.saveAndFlush(new User("alice", "b@x.com", "H2")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsByEmail returns true only after persistence")
    void existsByEmail() {
        assertThat(userRepository.existsByEmail("alice@x.com")).isFalse();
        userRepository.saveAndFlush(new User("alice", "alice@x.com", "HASH"));
        assertThat(userRepository.existsByEmail("alice@x.com")).isTrue();
    }
}
