package com.vendorfraud.integration;

import com.vendorfraud.bankchange.ApprovalStage;
import com.vendorfraud.reference.ReferenceGeneratorService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Concurrency.
 *
 * <p>H2 serialises more aggressively than MySQL, so these tests prove
 * the invariants (uniqueness, single-winner) rather than measuring
 * throughput. DATABASE_SETUP.md describes running the same suite
 * against MySQL 8, where the row locks are exercised properly.
 */
class ConcurrencyIntegrationTest extends WorkflowScenario {

    @Autowired private ReferenceGeneratorService referenceGenerator;

    @BeforeEach
    void seed() {
        seedOrganisation();
    }

    @Test
    @DisplayName("concurrent reference generation never produces a duplicate")
    void concurrentReferenceGenerationIsUnique() throws Exception {
        int threads = 8;
        int perThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> references = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        references.add(referenceGenerator.nextPaymentReference());
                    }
                } catch (Throwable ex) {
                    failures.add(ex);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).isEmpty();
        assertThat(references).hasSize(threads * perThread);
        assertThat(Set.copyOf(references)).hasSameSizeAs(references);
        assertThat(references).allMatch(r -> r.matches("PAY-\\d{4}-\\d{6}"));
    }

    @Test
    @DisplayName("two simultaneous decisions on one task leave exactly one winner")
    void concurrentDecisionsProduceOneWinner() throws Exception {
        long requestId = longField(createRequestAsMaker(), "id");
        TaskRef task = pendingTask(requestId, ApprovalStage.FIRST);
        String token = tokenFor(task.assigneeUsername());
        String body = json(Map.of("approved", true,
                "comment", "Evidence verified against the vendor letterhead"));

        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    int status = mockMvc.perform(authed(
                                    post("/api/approvals/tasks/" + task.id() + "/decision"), token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn().getResponse().getStatus();
                    if (status == 200) {
                        accepted.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                } catch (Throwable ex) {
                    failures.add(ex);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).isEmpty();
        assertThat(accepted.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(attempts - 1);

        // One approved task, and exactly one second-stage task behind it.
        List<TaskRef> tasks = taskRefs(requestId);
        assertThat(tasks).hasSize(2);
        assertThat(tasks.stream()
                .filter(t -> t.stage() == ApprovalStage.SECOND)
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("two simultaneous requests for one vendor leave exactly one open")
    void concurrentDuplicateRequestsAreRejected() throws Exception {
        String token = tokenFor(maker.getUsername());
        String body = json(bankChangeBody(vendor.getId()));

        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    statuses.add(mockMvc.perform(authed(post("/api/bank-change-requests"), token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn().getResponse().getStatus());
                } catch (Exception ex) {
                    statuses.add(-1);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(requestRepository.findOpenByVendor(vendor.getId())).hasSize(1);
    }

    @Test
    @DisplayName("concurrent scheduler passes activate a request only once")
    void concurrentSchedulerPassesActivateOnce() throws Exception {
        long requestId = requestInCoolingOff();
        txTemplate.executeWithoutResult(status -> {
            var request = requestRepository.findById(requestId).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(request,
                    "coolingOffEndAt", java.time.Instant.now().minusSeconds(60));
            requestRepository.saveAndFlush(request);
        });

        int passes = 4;
        ExecutorService pool = Executors.newFixedThreadPool(passes);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger handled = new AtomicInteger();

        for (int i = 0; i < passes; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    handled.addAndGet(scheduler.runOnce());
                } catch (Exception ignored) {
                    // Counted by the assertions below, not here.
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        // However many passes ran, exactly one new banking record exists.
        assertThat(bankAccountRepository.findHistoryByVendor(vendor.getId())).hasSize(2);
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(com.vendorfraud.bankchange.BankChangeStatus.ACTIVATED);
    }

    @Autowired private com.vendorfraud.scheduler.CoolingOffScheduler scheduler;
    @Autowired private com.vendorfraud.vendor.VendorBankAccountRepository bankAccountRepository;
}
