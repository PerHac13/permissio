package com.perhac.permissio.authorization.evaluator;

import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import org.springframework.core.Ordered;

/**
 * Strategy interface for authorization policy evaluation steps in the decision pipeline.
 * Implementations are executed in ascending order according to {@link Ordered#getOrder()}.
 */
public interface PolicyEvaluator extends Ordered {

    /**
     * Evaluates the given authorization context against this policy.
     *
     * @param context the authorization context
     * @return a {@link Decision} indicating allow or deny
     */
    Decision evaluate(AuthorizationContext context);

    /**
     * The unique identifier/name of this evaluator.
     *
     * @return evaluator name
     */
    String name();
}
