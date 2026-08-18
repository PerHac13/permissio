package com.perhac.permissio.authorization.model;

/**
 * Immutable decision result emitted by policy evaluators and the authorization engine.
 *
 * @param allowed   whether the requested action is permitted
 * @param reason    optional denial reason code (null when allowed)
 * @param evaluator the identifier of the evaluator that produced this decision
 */
public record Decision(boolean allowed, String reason, String evaluator) {

    /**
     * Creates an allowed decision.
     *
     * @param evaluator the evaluator name
     * @return an allowed Decision instance
     */
    public static Decision allow(String evaluator) {
        return new Decision(true, null, evaluator);
    }

    /**
     * Creates a denied decision with a reason code.
     *
     * @param reason    the denial reason code
     * @param evaluator the evaluator name
     * @return a denied Decision instance
     */
    public static Decision deny(String reason, String evaluator) {
        return new Decision(false, reason, evaluator);
    }
}
