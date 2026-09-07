package com.medicare.identity.repository;

import com.medicare.identity.entity.AccountProvisioningRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountProvisioningRequestRepository
        extends JpaRepository<AccountProvisioningRequest, UUID> {

}