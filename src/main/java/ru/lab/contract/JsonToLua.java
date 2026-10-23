package ru.lab.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Явный мост между деревом Jackson и значениями интерпретатора. */
final class JsonToLua {
    static final LuaValue JSON_NULL = LuaValue.userdataOf(new Object());
    private static final double MAX_SAFE_INTEGER = 9_007_199_254_740_991d;

    private JsonToLua() {}

    static LuaValue convert(JsonNode node) {
        if (node == null || node.isNull()) return JSON_NULL;
        if (node.isBoolean()) return LuaValue.valueOf(node.booleanValue());
        if (node.isTextual()) return LuaValue.valueOf(node.textValue());
        if (node.isNumber()) {
            double value = node.doubleValue();
            if (!Double.isFinite(value) || (node.isIntegralNumber() && Math.abs(value) > MAX_SAFE_INTEGER)) {
                throw new IllegalArgumentException("Число вне поддерживаемого диапазона Lua: " + node);
            }
            return LuaValue.valueOf(value);
        }
        LuaTable table = new LuaTable();
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) table.set(i + 1, convert(node.get(i)));
        } else if (node.isObject()) {
            node.fields().forEachRemaining(entry -> table.set(entry.getKey(), convert(entry.getValue())));
        } else {
            throw new IllegalArgumentException("Неподдерживаемый тип JSON: " + node.getNodeType());
        }
        return table;
    }
}
