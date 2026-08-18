package com.perhac.permissio.authorization.model;

import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.relationship.entity.Relationship;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.subject.entity.Subject;

import java.util.List;
import java.util.UUID;

/**
 * Immutable context containing all entities and parameters needed for authorization evaluation.
 *
 * @param clientId      the tenant ID under which the request is evaluated
 * @param subject       the authenticated subject requesting access
 * @param resource      the target resource being accessed
 * @param action        the action attempting to be performed
 * @param relationships all relationship tuples held by the subject within the tenant
 */
public record AuthorizationContext(
        UUID clientId,
        Subject subject,
        Resource resource,
        Action action,
        List<Relationship> relationships
) {
    public AuthorizationContext {
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
    }
}
