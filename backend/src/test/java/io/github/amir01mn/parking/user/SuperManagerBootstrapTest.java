package io.github.amir01mn.parking.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.amir01mn.parking.config.BootstrapProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

class SuperManagerBootstrapTest {

    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);

    private SuperManagerBootstrap bootstrap(String email, String password) {
        BootstrapProperties properties = new BootstrapProperties(
                new BootstrapProperties.SuperManager(email, password, "Super", "Manager"));
        return new SuperManagerBootstrap(properties, users, NoOpPasswordEncoder.getInstance(), clock);
    }

    @Test
    void createsSuperManagerWhenAbsent() {
        when(users.existsByEmail("admin@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bootstrap(" Admin@Example.com ", "Admin#2030").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.SUPER_MANAGER);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("Admin#2030");
    }

    @Test
    void leavesExistingAccountAlone() {
        when(users.existsByEmail("admin@example.com")).thenReturn(true);

        bootstrap("admin@example.com", "Admin#2030").run(null);

        verify(users, never()).save(any());
    }

    @Test
    void skipsWhenConfigIsBlank() {
        bootstrap("", "Admin#2030").run(null);
        bootstrap("admin@example.com", " ").run(null);
        bootstrap(null, null).run(null);

        verify(users, never()).existsByEmail(any());
        verify(users, never()).save(any());
    }
}
