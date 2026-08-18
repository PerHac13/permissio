package com.perhac.permissio.authorization.service;

import com.perhac.permissio.authorization.dto.AuthorizeRequest;
import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.common.exception.NotFoundException;
import com.perhac.permissio.relationship.entity.Relationship;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.subject.entity.Subject;
import com.perhac.permissio.subject.repository.SubjectRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Builds the tenant-isolated {@link AuthorizationContext} for an incoming {@link AuthorizeRequest}.
 * <p>
 * Ensures both Subject and Resource exist under the resolved tenant (clientId). Throws
 * {@link NotFoundException} if either entity is missing or belongs to a different tenant.
 */
@Component
public class AuthorizationContextBuilder {

    private final SubjectRepository subjectRepository;
    private final ResourceRepository resourceRepository;
    private final RelationshipRepository relationshipRepository;

    public AuthorizationContextBuilder(
            SubjectRepository subjectRepository,
            ResourceRepository resourceRepository,
            RelationshipRepository relationshipRepository) {
        this.subjectRepository = subjectRepository;
        this.resourceRepository = resourceRepository;
        this.relationshipRepository = relationshipRepository;
    }

    /**
     * Resolves entities and constructs the evaluation context.
     *
     * @param clientId the tenant ID resolved from authentication
     * @param request  the incoming authorization request
     * @return populated {@link AuthorizationContext}
     * @throws NotFoundException if subject or resource is not found under the given tenant
     */
    public AuthorizationContext build(UUID clientId, AuthorizeRequest request) {
        Subject subject = subjectRepository.findByClientIdAndId(clientId, request.getSubjectId())
                .orElseThrow(() -> new NotFoundException("Subject not found"));

        Resource resource = resourceRepository.findByClientIdAndId(clientId, request.getResourceId())
                .orElseThrow(() -> new NotFoundException("Resource not found"));

        List<Relationship> relationships = relationshipRepository.findByClientIdAndSubjectId(clientId, subject.getId());

        return new AuthorizationContext(clientId, subject, resource, request.getAction(), relationships);
    }
}
