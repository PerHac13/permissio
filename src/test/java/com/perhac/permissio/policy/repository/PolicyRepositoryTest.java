package com.perhac.permissio.policy.repository;

import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.policy.entity.Policy;
import com.perhac.permissio.policy.entity.PolicyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PolicyRepositoryTest {

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private ClientRepository clientRepository;

    private Client clientA;
    private Client clientB;

    @BeforeEach
    void setUp() {
        policyRepository.deleteAll();
        clientRepository.deleteAll();

        clientA = clientRepository.save(Client.builder()
                .name("Tenant A")
                .apiKeyHash("hash-a")
                .createdAt(Instant.now())
                .build());

        clientB = clientRepository.save(Client.builder()
                .name("Tenant B")
                .apiKeyHash("hash-b")
                .createdAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Saves and retrieves a Policy entity")
    void saveAndRetrievePolicy() {
        Policy policy = policyRepository.save(Policy.builder()
                .clientId(clientA.getId())
                .resourceType("document")
                .action(Action.READ)
                .policyType(PolicyType.ABAC)
                .expression("#subject['dept'] == #resource['dept']")
                .createdAt(Instant.now())
                .build());

        assertThat(policy.getId()).isNotNull();

        Optional<Policy> found = policyRepository.findByClientIdAndId(clientA.getId(), policy.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getResourceType()).isEqualTo("document");
        assertThat(found.get().getAction()).isEqualTo(Action.READ);
        assertThat(found.get().getPolicyType()).isEqualTo(PolicyType.ABAC);
        assertThat(found.get().getExpression()).isEqualTo("#subject['dept'] == #resource['dept']");
    }

    @Test
    @DisplayName("Tenant isolation: Client A cannot see Client B's policy")
    void tenantIsolation_cannotAccessOtherTenantsPolicy() {
        Policy policyB = policyRepository.save(Policy.builder()
                .clientId(clientB.getId())
                .resourceType("document")
                .action(Action.UPDATE)
                .policyType(PolicyType.ABAC)
                .expression("#subject['clearance'] >= 3")
                .createdAt(Instant.now())
                .build());

        Optional<Policy> queryFromA = policyRepository.findByClientIdAndId(clientA.getId(), policyB.getId());
        assertThat(queryFromA).isEmpty();

        List<Policy> listFromA = policyRepository.findByClientIdAndResourceTypeAndActionAndPolicyType(
                clientA.getId(), "document", Action.UPDATE, PolicyType.ABAC
        );
        assertThat(listFromA).isEmpty();
    }

    @Test
    @DisplayName("Queries policies matching resourceType, action, and policyType")
    void findByResourceTypeActionAndPolicyType() {
        policyRepository.save(Policy.builder()
                .clientId(clientA.getId())
                .resourceType("document")
                .action(Action.UPDATE)
                .policyType(PolicyType.ABAC)
                .expression("#subject['dept'] == #resource['dept']")
                .createdAt(Instant.now())
                .build());

        policyRepository.save(Policy.builder()
                .clientId(clientA.getId())
                .resourceType("document")
                .action(Action.UPDATE)
                .policyType(PolicyType.BUSINESS_RULE)
                .expression("#environment['currentHour'] < 18")
                .createdAt(Instant.now())
                .build());

        List<Policy> abacPolicies = policyRepository.findByClientIdAndResourceTypeAndActionAndPolicyType(
                clientA.getId(), "document", Action.UPDATE, PolicyType.ABAC
        );
        assertThat(abacPolicies).hasSize(1);
        assertThat(abacPolicies.get(0).getExpression()).contains("dept");

        List<Policy> rulePolicies = policyRepository.findByClientIdAndResourceTypeAndActionAndPolicyType(
                clientA.getId(), "document", Action.UPDATE, PolicyType.BUSINESS_RULE
        );
        assertThat(rulePolicies).hasSize(1);
        assertThat(rulePolicies.get(0).getExpression()).contains("currentHour");
    }
}
