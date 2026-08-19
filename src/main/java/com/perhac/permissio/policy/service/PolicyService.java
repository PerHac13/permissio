package com.perhac.permissio.policy.service;

import com.perhac.permissio.common.exception.NotFoundException;
import com.perhac.permissio.policy.dto.CreatePolicyRequest;
import com.perhac.permissio.policy.dto.PolicyResponse;
import com.perhac.permissio.policy.entity.Policy;
import com.perhac.permissio.policy.entity.PolicyType;
import com.perhac.permissio.policy.repository.PolicyRepository;
import com.perhac.permissio.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing tenant-scoped {@link Policy} definitions.
 */
@Service
@Transactional
public class PolicyService {

    private final PolicyRepository policyRepository;

    public PolicyService(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    /**
     * Creates a new policy rule for the current tenant.
     */
    public PolicyResponse createPolicy(CreatePolicyRequest request) {
        UUID clientId = TenantContext.get();

        Policy policy = Policy.builder()
                .clientId(clientId)
                .resourceType(request.getResourceType())
                .action(request.getAction())
                .policyType(request.getPolicyType())
                .expression(request.getExpression())
                .createdAt(Instant.now())
                .build();

        Policy saved = policyRepository.save(policy);
        return PolicyResponse.fromEntity(saved);
    }

    /**
     * Retrieves a policy by its ID for the current tenant.
     */
    @Transactional(readOnly = true)
    public PolicyResponse getPolicyById(UUID id) {
        UUID clientId = TenantContext.get();
        Policy policy = policyRepository.findByClientIdAndId(clientId, id)
                .orElseThrow(() -> new NotFoundException("Policy not found"));
        return PolicyResponse.fromEntity(policy);
    }

    /**
     * Lists policies belonging to the current tenant with optional filters.
     */
    @Transactional(readOnly = true)
    public List<PolicyResponse> listPolicies(String resourceType, PolicyType policyType) {
        UUID clientId = TenantContext.get();

        List<Policy> policies;
        if (resourceType != null && policyType != null) {
            policies = policyRepository.findByClientIdAndResourceTypeAndPolicyType(clientId, resourceType, policyType);
        } else {
            policies = policyRepository.findAllByClientId(clientId);
        }

        return policies.stream()
                .map(PolicyResponse::fromEntity)
                .toList();
    }

    /**
     * Deletes a policy belonging to the current tenant.
     */
    public void deletePolicy(UUID id) {
        UUID clientId = TenantContext.get();
        Policy policy = policyRepository.findByClientIdAndId(clientId, id)
                .orElseThrow(() -> new NotFoundException("Policy not found"));
        policyRepository.delete(policy);
    }
}
