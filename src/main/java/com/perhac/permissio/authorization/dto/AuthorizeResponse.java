package com.perhac.permissio.authorization.dto;

/**
 * Response body for {@code POST /api/v1/authorize}.
 *
 * @param allowed   whether the requested action is permitted
 * @param reason    optional denial reason code (null if allowed)
 * @param evaluator the evaluator that determined the final outcome
 */
public record AuthorizeResponse(
        boolean allowed,
        String reason,
        String evaluator
) {
}
