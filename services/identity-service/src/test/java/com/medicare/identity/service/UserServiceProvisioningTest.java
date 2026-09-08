package com.medicare.identity.service;

import com.medicare.identity.AbstractIntegrationTest;
import com.medicare.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceProvisioningTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void concurrentProvisioningWithSameEmailDifferentKeys_onlyOneSucceeds() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@test.local";
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();

        var futures = new CompletableFuture[threads];
        for (int i = 0; i < threads; i++) {
            UUID key = UUID.randomUUID(); // different idempotency key each time
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    startGate.await();
                    var result = userService.provisionPatientAccount(key, email, "Test", "Patient", "patient-service");
                    if (result instanceof UserService.ProvisioningResult.Created) {
                        created.incrementAndGet();
                    } else {
                        conflicted.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, pool);
        }

        startGate.countDown(); // release all threads at once
        CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(created.get()).isEqualTo(1);
        assertThat(conflicted.get()).isEqualTo(threads - 1);
        assertThat(userRepository.existsByEmail(email)).isTrue();
    }

    @Test
    void sameIdempotencyKeyCalledTwice_returnsSameResult() throws Exception {
        UUID key = UUID.randomUUID();
        String email = "retry-" + UUID.randomUUID() + "@test.local";

        var first = userService.provisionPatientAccount(key, email, "Test", "Patient", "patient-service");
        var second = userService.provisionPatientAccount(key, email, "Test", "Patient", "patient-service");

        assertThat(first).isInstanceOf(UserService.ProvisioningResult.Created.class);
        assertThat(second).isInstanceOf(UserService.ProvisioningResult.Created.class);
        assertThat(((UserService.ProvisioningResult.Created) first).userId())
                .isEqualTo(((UserService.ProvisioningResult.Created) second).userId());
    }
}
