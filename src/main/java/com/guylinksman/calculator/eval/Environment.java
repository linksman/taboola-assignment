package com.guylinksman.calculator.eval;

import com.guylinksman.calculator.ast.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Variable state shared across all statements. A {@link LinkedHashMap} gives
 * "first appearance" ordering (SPEC REQ-007) for free, since re-putting an
 * existing key does not change its iteration position.
 */
public final class Environment {

    private final Map<String, Value> variables = new LinkedHashMap<>();

    public boolean has(String name) {
        return variables.containsKey(name);
    }

    public Value get(String name) {
        return variables.get(name);
    }

    public void set(String name, Value value) {
        variables.put(name, value);
    }

    public Map<String, Value> asMap() {
        return variables;
    }
}
