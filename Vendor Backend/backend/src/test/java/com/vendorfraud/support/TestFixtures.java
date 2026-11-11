package com.vendorfraud.support;

import com.vendorfraud.user.Role;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.RoleRepository;
import com.vendorfraud.user.User;
import com.vendorfraud.user.UserRepository;
import com.vendorfraud.vendor.Vendor;
import com.vendorfraud.vendor.VendorBankAccount;
import com.vendorfraud.vendor.VendorBankAccountRepository;
import com.vendorfraud.vendor.VendorRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the reference data that Flyway would normally provide, plus
 * the users and vendors each test needs.
 *
 * <p>Roles and reference sequences go in through native SQL for the
 * same reason the production code does not expose constructors for
 * them: they are reference data, not something the application
 * creates.
 */
@Component
public class TestFixtures {

    public static final String PASSWORD = "Test-Password-1";

    private final EntityManager entityManager;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final VendorBankAccountRepository bankAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public TestFixtures(EntityManager entityManager, RoleRepository roleRepository,
                        UserRepository userRepository, VendorRepository vendorRepository,
                        VendorBankAccountRepository bankAccountRepository,
                        PasswordEncoder passwordEncoder) {
        this.entityManager = entityManager;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.vendorRepository = vendorRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Wipes every table, then reinstates reference data. */
    @Transactional
    public void reset() {
        List<String> tables = List.of("audit_logs", "notifications", "approval_assignment_history",
                "payment_holds", "payments", "emergency_overrides", "approval_tasks",
                "bank_change_requests", "vendor_bank_accounts", "vendors", "user_roles",
                "users", "roles", "reference_sequences");
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        for (String table : tables) {
            entityManager.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
        seedReferenceData();
        entityManager.flush();
        entityManager.clear();
    }

    private void seedReferenceData() {
        insertRole(RoleName.MAKER, "Raises vendor bank-change requests");
        insertRole(RoleName.APPROVER, "Independently approves or rejects bank-change requests");
        insertRole(RoleName.ACCOUNTS_PAYABLE, "Manages payments, holds and override requests");
        insertRole(RoleName.ADMINISTRATOR, "Decides emergency overrides and reads audit");

        insertSequence("BANK_CHANGE_REQUEST", "BCR");
        insertSequence("PAYMENT", "PAY");
        insertSequence("EMERGENCY_OVERRIDE", "EOV");
    }

    private void insertRole(RoleName name, String description) {
        entityManager.createNativeQuery(
                        "INSERT INTO roles (name, description, created_at) "
                                + "VALUES (?, ?, CURRENT_TIMESTAMP)")
                .setParameter(1, name.name())
                .setParameter(2, description)
                .executeUpdate();
    }

    private void insertSequence(String name, String prefix) {
        entityManager.createNativeQuery(
                        "INSERT INTO reference_sequences "
                                + "(sequence_name, prefix, year_part, next_value, updated_at) "
                                + "VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP)")
                .setParameter(1, name)
                .setParameter(2, prefix)
                .setParameter(3, java.time.Year.now().getValue())
                .executeUpdate();
    }

    @Transactional
    public User createUser(String username, String fullName, boolean active, RoleName... roles) {
        User user = new User(username, username + "@test.example", fullName,
                passwordEncoder.encode(PASSWORD));
        for (RoleName roleName : roles) {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            user.addRole(role);
        }
        if (!active) {
            user.deactivate(null);
        }
        return userRepository.saveAndFlush(user);
    }

    @Transactional
    public Vendor createVendor(String code, String name, boolean active) {
        Vendor vendor = new Vendor(code, name, "TAXREF-" + code, code.toLowerCase() + "@test.example");
        if (!active) {
            vendor.deactivate(null);
        }
        return vendorRepository.saveAndFlush(vendor);
    }

    /** The live account reference of a vendor, read inside a transaction. */
    @Transactional(readOnly = true)
    public String activeAccountReference(Long vendorId) {
        return bankAccountRepository
                .findByVendorIdAndStatus(vendorId, com.vendorfraud.vendor.BankAccountStatus.ACTIVE)
                .map(VendorBankAccount::getAccountReference)
                .orElseThrow();
    }

    /** A vendor with an active, synthetic banking record. */
    @Transactional
    public Vendor createVendorWithBankAccount(String code, String name) {
        Vendor vendor = createVendor(code, name, true);
        VendorBankAccount account = new VendorBankAccount(vendor, name,
                "Synthetic Bank of Avalon", "SYN-AC-" + Math.abs(code.hashCode() % 1000000),
                "SYNRT-0001", null);
        bankAccountRepository.saveAndFlush(account);
        return vendor;
    }
}
