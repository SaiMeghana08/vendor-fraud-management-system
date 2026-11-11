package com.vendorfraud.config;

import com.vendorfraud.user.Role;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.RoleRepository;
import com.vendorfraud.user.User;
import com.vendorfraud.user.UserRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controlled seed mechanism for development users.
 *
 * <p>This is the only way a user is ever created in this application.
 * There is no registration endpoint, no self-service role assignment
 * and no user-management screen. The component is inert unless
 * {@code app.bootstrap.enabled=true} and a password is supplied in
 * configuration, so an environment that forgets to disable it still
 * cannot be seeded with a guessable password.
 *
 * <p>Existing users are never modified: the component only fills gaps.
 */
@Component
public class DevDataBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataBootstrap.class);

    /** username -> (full name, roles). One account per role, plus a dual-role approver. */
    private static final Map<String, SeedUser> SEED_USERS = Map.of(
            "maker.one", new SeedUser("Maya Kerr", "maker.one@vendorfraud.example",
                    List.of(RoleName.MAKER)),
            "maker.two", new SeedUser("Daniel Osei", "maker.two@vendorfraud.example",
                    List.of(RoleName.MAKER)),
            "approver.one", new SeedUser("Priya Raman", "approver.one@vendorfraud.example",
                    List.of(RoleName.APPROVER)),
            "approver.two", new SeedUser("Tomas Lind", "approver.two@vendorfraud.example",
                    List.of(RoleName.APPROVER)),
            "approver.three", new SeedUser("Grace Abara", "approver.three@vendorfraud.example",
                    List.of(RoleName.APPROVER)),
            "ap.one", new SeedUser("Ravi Chandra", "ap.one@vendorfraud.example",
                    List.of(RoleName.ACCOUNTS_PAYABLE)),
            "admin.one", new SeedUser("Helen Vance", "admin.one@vendorfraud.example",
                    List.of(RoleName.ADMINISTRATOR)),
            "inactive.maker", new SeedUser("Former Employee", "former@vendorfraud.example",
                    List.of(RoleName.MAKER)));

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataBootstrap(BootstrapProperties properties, UserRepository userRepository,
                            RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.debug("Bootstrap disabled; no users created");
            return;
        }
        String password = properties.defaultPassword();
        if (password == null || password.isBlank()) {
            log.warn("Bootstrap is enabled but app.bootstrap.default-password is not set; "
                    + "no users were created");
            return;
        }

        String hash = passwordEncoder.encode(password);
        int created = 0;

        for (var entry : SEED_USERS.entrySet()) {
            String username = entry.getKey();
            if (userRepository.existsByUsername(username)) {
                continue;
            }
            SeedUser seed = entry.getValue();
            User user = new User(username, seed.email(), seed.fullName(), hash);
            for (RoleName roleName : seed.roles()) {
                Role role = roleRepository.findByName(roleName).orElseThrow(
                        () -> new IllegalStateException("Role " + roleName + " is missing; "
                                + "run the Flyway migrations first"));
                user.addRole(role);
            }
            // One deliberately deactivated account, so the "inactive
            // user cannot log in" rule is demonstrable out of the box.
            if ("inactive.maker".equals(username)) {
                user.deactivate(null);
            }
            userRepository.save(user);
            created++;
        }

        if (created > 0) {
            log.info("Bootstrap created {} development user(s). Roles are fixed in code; "
                    + "there is no endpoint that can change them.", created);
        }
    }

    private record SeedUser(String fullName, String email, List<RoleName> roles) {
    }
}
