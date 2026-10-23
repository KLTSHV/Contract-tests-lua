package ru.lab.contract;

import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Map<String, String> options = parse(args);
            if (options.containsKey("--help")) { out.println(HELP); return 0; }
            if (options.containsKey("--demo-server")) {
                if (options.size() != 1) throw new IllegalArgumentException("--demo-server используется отдельно");
                int port = Integer.parseInt(options.get("--demo-server"));
                if (port < 1 || port > 65535) throw new IllegalArgumentException("Порт: от 1 до 65535");
                var server = DemoServer.start(port);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
                out.println("Demo API: http://127.0.0.1:" + port + "/users/42 (Ctrl+C — выход)");
                try { new CountDownLatch(1).await(); } finally { server.stop(0); }
                return 0;
            }
            if (options.containsKey("--file") && options.containsKey("--url")) {
                throw new IllegalArgumentException("Укажите только один источник: --file или --url");
            }
            Path contract = Path.of(options.getOrDefault("--contract", "contracts/user.lua"));
            ResponseSource source = options.containsKey("--url")
                    ? ResponseSource.http(URI.create(options.get("--url")))
                    : ResponseSource.file(Path.of(options.getOrDefault("--file", "examples/user.json")));
            boolean watch = options.containsKey("--watch");
            if (!watch && options.containsKey("--interval")) throw new IllegalArgumentException("--interval требует --watch");
            long interval = Long.parseLong(options.getOrDefault("--interval", "1000"));
            if (interval < 100 || interval > 60_000) throw new IllegalArgumentException("Интервал: от 100 до 60000 мс");
            ContractRunner runner = new ContractRunner();
            if (watch) out.println("WATCH: перечитываю контракт и ответ при каждой проверке. Ctrl+C — выход.");
            do {
                ContractRunner.Result result = runner.check(contract, source);
                out.printf("%s %s — %s%n", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        result.status(), result.message());
                if (!watch) return result.exitCode();
                Thread.sleep(interval);
            } while (true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        } catch (Exception e) {
            err.println("ERROR — " + e.getMessage());
            err.println("Справка: java -jar target/contract-lab.jar --help");
            return 2;
        }
    }

    private static Map<String, String> parse(String[] args) {
        Set<String> flags = Set.of("--help", "--watch");
        Set<String> valued = Set.of("--contract", "--file", "--url", "--interval", "--demo-server");
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (options.containsKey(key)) throw new IllegalArgumentException("Повтор параметра: " + key);
            if (flags.contains(key)) options.put(key, "true");
            else if (valued.contains(key)) {
                if (i + 1 == args.length || args[i + 1].startsWith("--")) throw new IllegalArgumentException("Нет значения для " + key);
                options.put(key, args[++i]);
            } else throw new IllegalArgumentException("Неизвестный параметр: " + key);
        }
        return options;
    }

    private static final String HELP = """
            Contract Lab — Java + Lua
            java -jar target/contract-lab.jar [параметры]
              --contract PATH     Lua-контракт (contracts/user.lua)
              --file PATH         JSON-файл (examples/user.json)
              --url URL           HTTP GET вместо файла
              --watch             Повторять проверки; перечитывать Lua и JSON
              --interval MS       Пауза между проверками, 100..60000 (1000)
              --demo-server PORT  Запустить локальный API отдельным процессом
              --help              Справка
            Код выхода: 0 PASS, 1 FAIL, 2 ERROR; Ctrl+C завершает watch.
            """;
}
