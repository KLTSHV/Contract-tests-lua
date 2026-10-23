package ru.lab.contract;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class ContractRunner {
    enum Status { PASS, FAIL, ERROR }

    record Result(Status status, String message) {
        int exitCode() {
            return switch (status) { case PASS -> 0; case FAIL -> 1; case ERROR -> 2; };
        }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    Result check(Path contract, ResponseSource source) throws InterruptedException {
        try {
            // При каждом запуске проверки читаем актуальный внешний файл.
            String script = Files.readString(contract, StandardCharsets.UTF_8);
            ResponseSource.Response response = source.read();
            JsonNode json = mapper.readTree(response.body());
            if (json == null || json.isMissingNode()) throw new IOException("Пустой JSON-ответ");

            // Новое окружение не переносит глобальные переменные между проверками.
            Globals globals = JsePlatform.standardGlobals();
            globals.set("JSON_NULL", JsonToLua.JSON_NULL);
            globals.load(script, "@" + contract).call();
            LuaValue validate = globals.get("validate");
            if (!validate.isfunction()) throw new IllegalArgumentException("Контракт должен объявлять функцию validate(body, response)");

            LuaTable metadata = new LuaTable();
            metadata.set("status", response.status());
            LuaTable headers = new LuaTable();
            response.headers().forEach((name, values) ->
                    headers.set(name.toLowerCase(Locale.ROOT), String.join(", ", values)));
            metadata.set("headers", headers);

            Varargs result = validate.invoke(LuaValue.varargsOf(new LuaValue[]{JsonToLua.convert(json), metadata}));
            if (result.arg1().type() != LuaValue.TBOOLEAN || result.arg(2).type() != LuaValue.TSTRING) {
                throw new IllegalArgumentException("validate должна вернуть boolean, string; например: true, 'OK'");
            }
            return new Result(result.arg1().toboolean() ? Status.PASS : Status.FAIL, result.arg(2).tojstring());
        } catch (IOException | RuntimeException e) {
            String message = e.getMessage();
            return new Result(Status.ERROR, message == null ? e.getClass().getSimpleName() : message);
        }
    }
}
