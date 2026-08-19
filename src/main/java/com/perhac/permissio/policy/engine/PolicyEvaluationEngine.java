package com.perhac.permissio.policy.engine;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Sandboxed policy expression evaluator using Spring Expression Language (SpEL).
 * <p>
 * Evaluates dynamic ABAC and Business Rule policies against Subject/Resource attributes
 * and environmental context. Uses {@link SimpleEvaluationContext} to strictly prevent
 * arbitrary code execution, Java reflection, and ClassLoader manipulation.
 */
@Component
public class PolicyEvaluationEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluationEngine.class);

    private final ExpressionParser parser;
    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    public PolicyEvaluationEngine(ObjectMapper objectMapper) {
        this.parser = new SpelExpressionParser();
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates a boolean SpEL expression within a secure read-only context.
     *
     * @param expressionString the boolean policy expression (e.g. {@code #subject['dept'] == #resource['dept']})
     * @param subjectJson      raw JSON attributes of the Subject
     * @param resourceJson     raw JSON attributes of the Resource
     * @param action           the action being performed
     * @param environment      environmental context map (e.g. currentHour, dayOfWeek)
     * @return {@code true} if the expression evaluates to true, {@code false} otherwise
     */
    public boolean evaluate(
            String expressionString,
            String subjectJson,
            String resourceJson,
            String action,
            Map<String, Object> environment) {

        if (expressionString == null || expressionString.isBlank()) {
            return true;
        }

        try {
            Map<String, Object> subjectMap = parseJsonAttributes(subjectJson);
            Map<String, Object> resourceMap = parseJsonAttributes(resourceJson);
            Map<String, Object> envMap = environment != null ? environment : Collections.emptyMap();

            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            context.setVariable("subject", subjectMap);
            context.setVariable("resource", resourceMap);
            context.setVariable("action", action != null ? action : "");
            context.setVariable("environment", envMap);

            Expression expression = parser.parseExpression(expressionString);
            Boolean result = expression.getValue(context, Boolean.class);

            return Boolean.TRUE.equals(result);
        } catch (Exception ex) {
            log.debug("Policy expression evaluation failed for '{}': {}", expressionString, ex.getMessage());
            return false;
        }
    }

    private Map<String, Object> parseJsonAttributes(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE_REF);
        } catch (Exception ex) {
            log.debug("Failed to parse JSON attributes: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }
}
