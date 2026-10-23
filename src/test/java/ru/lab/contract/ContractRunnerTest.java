package ru.lab.contract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static ru.lab.contract.ContractRunner.Status.*;

class ContractRunnerTest {
    @TempDir Path temp;
    private final ContractRunner runner = new ContractRunner();
    private final Path userContract = Path.of("contracts/user.lua");

    private ResponseSource json(String body) {
        return () -> new ResponseSource.Response(body, 200, Map.of("content-type", List.of("application/json")));
    }

    private Path script(String text) throws Exception {
        return Files.writeString(temp.resolve("contract.lua"), text);
    }

    @Test void validatesFixtures() throws Exception {
        assertEquals(PASS, runner.check(userContract, ResponseSource.file(Path.of("examples/user.json"))).status());
        var failed = runner.check(userContract, ResponseSource.file(Path.of("examples/user-invalid.json")));
        assertEquals(FAIL, failed.status());
        assertTrue(failed.message().contains("id"));
    }

    @Test void reloadsChangedScriptInSameRunner() throws Exception {
        Path contract = script(Files.readString(userContract));
        var source = json("{\"id\":42,\"name\":\"Анна\",\"age\":20}");
        assertEquals(PASS, runner.check(contract, source).status());
        Files.writeString(contract, Files.readString(contract).replace("MIN_AGE = 18", "MIN_AGE = 21"));
        var result = runner.check(contract, source);
        assertEquals(FAIL, result.status());
        assertTrue(result.message().contains("21"));
    }

    @Test void recoversAfterSyntaxAndRuntimeErrors() throws Exception {
        Path contract = script("function validate(");
        assertEquals(ERROR, runner.check(contract, json("{}" )).status());
        Files.writeString(contract, "function validate() error('broken contract') end");
        assertEquals(ERROR, runner.check(contract, json("{}")).status());
        Files.writeString(contract, "function validate() return true, 'fixed' end");
        assertEquals(PASS, runner.check(contract, json("{}")).status());
    }

    @Test void rejectsMissingFunctionAndWrongReturnTypes() throws Exception {
        for (String source : List.of("x = 1", "validate = 42",
                "function validate() return 'yes', 'OK' end",
                "function validate() return true, 123 end", "function validate() end")) {
            assertEquals(ERROR, runner.check(script(source), json("{}")).status(), source);
        }
    }

    @Test void handlesInvalidInputAndRecovers() throws Exception {
        Path contract = script("function validate() return true, 'OK' end");
        for (String body : List.of("", "{broken", "{} {}", "{\"id\":1,\"id\":2}", "9007199254740993")) {
            assertEquals(ERROR, runner.check(contract, json(body)).status(), body);
        }
        assertEquals(ERROR, runner.check(temp.resolve("missing.lua"), json("{}")).status());
        assertEquals(ERROR, runner.check(contract, ResponseSource.file(temp.resolve("missing.json"))).status());
        assertEquals(PASS, runner.check(contract, json("{}")).status());
    }

    @Test void convertsNestedJsonNullBooleanAndUtf8() throws Exception {
        Path contract = script("""
                function validate(body)
                    local ok = body.name == 'Анна' and body.enabled == false
                        and body.none == JSON_NULL and body.missing == nil
                        and #body.values == 3 and body.values[1] == 7
                        and body.values[2] == JSON_NULL and body.values[3].x == 1.5
                    return ok, 'mapping'
                end
                """);
        assertEquals(PASS, runner.check(contract, json("""
                {"name":"Анна","enabled":false,"none":null,"values":[7,null,{"x":1.5}]}
                """)).status());
    }

    @Test void createsFreshGlobalEnvironment() throws Exception {
        Path contract = script("count = (count or 0) + 1; function validate() return count == 1, 'isolated' end");
        assertEquals(PASS, runner.check(contract, json("{}")).status());
        assertEquals(PASS, runner.check(contract, json("{}")).status());
    }

    @Test void checksHttpStatusHeadersAndBody() throws Exception {
        var server = DemoServer.start(0);
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertEquals(PASS, runner.check(userContract, ResponseSource.http(URI.create(base + "/users/42"))).status());
            assertEquals(FAIL, runner.check(userContract, ResponseSource.http(URI.create(base + "/users/broken"))).status());
            var unavailable = runner.check(userContract, ResponseSource.http(URI.create(base + "/users/unavailable")));
            assertEquals(FAIL, unavailable.status());
            assertTrue(unavailable.message().contains("503"));
        } finally { server.stop(0); }
    }

    @Test void rejectsWrongContentType() throws Exception {
        assertEquals(FAIL, runner.check(userContract, () -> new ResponseSource.Response(
                "{\"id\":42,\"name\":\"Анна\",\"age\":20}", 200, Map.of("Content-Type", List.of("text/plain")))).status());
    }

    @Test void supportsAnotherContractWithoutChangingJava() throws Exception {
        Path order = Path.of("contracts/order.lua");
        assertEquals(PASS, runner.check(order, ResponseSource.file(Path.of("examples/order.json"))).status());
        assertEquals(FAIL, runner.check(order, json("{\"items\":[{\"price\":100,\"quantity\":2}],\"total\":100}")).status());
    }

    @Test void rejectsInvalidUserFields() throws Exception {
        for (String body : List.of("null", "[]", "{}", "{\"id\":0}", "{\"id\":1.5}",
                "{\"id\":1,\"name\":\"   \"}", "{\"id\":1,\"name\":\"Ann\",\"age\":17}",
                "{\"id\":1,\"name\":\"Ann\",\"age\":20.5}")) {
            assertEquals(FAIL, runner.check(userContract, json(body)).status(), body);
        }
    }

    @Test void cliReturnsUsefulExitCodesAndValidatesArguments() {
        var output = new PrintStream(new ByteArrayOutputStream());
        assertEquals(0, Main.run(new String[]{}, output, output));
        assertEquals(1, Main.run(new String[]{"--file", "examples/user-invalid.json"}, output, output));
        for (String[] args : List.of(new String[]{"--unknown"}, new String[]{"--file"},
                new String[]{"--watch", "--interval", "0"}, new String[]{"--interval", "1000"},
                new String[]{"--url", "ftp://localhost"}, new String[]{"--file", "x", "--url", "http://localhost"},
                new String[]{"--watch", "--watch"})) {
            assertEquals(2, Main.run(args, output, output));
        }
    }
}
