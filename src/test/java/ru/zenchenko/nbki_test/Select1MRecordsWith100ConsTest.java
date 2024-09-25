package ru.zenchenko.nbki_test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.zenchenko.nbki_test.model.User;
import ru.zenchenko.nbki_test.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class Select1MRecordsWith100ConsTest {
    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private UserRepository userRepository;

    //--------------------------------------------------------------------
    // константы для второго теста

    //100 соединений
    private static final int NUM_CONNECTIONS = 100;

    //заполним изначально бд таким кол-вом записей
    private static final int RECORDS_QUANTITY_TO_INSERT = 100_000;

    //сколько запросов будем отправлять в каждом потоке
    private static final int EACH_CONNECTION_SELECT_QUANTITY = 10000;

    // Тест для выборки 1 миллиона записей через 100 параллельных соединений
    // ограничение в 150 секунд
    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    public void testSelect1MRecordsWith100Connections() throws InterruptedException, ExecutionException {
        System.out.println("-------------------------------------------------------------------------");
        System.out.println("начинаем тест для выборки 1млн записей в 100 потоках (по 10 000 на каждый)");

        // начальное заполнение бд
        insertInitialRows();

        //засекаем время после заполнения бд
        long startTime = System.nanoTime();

        // выполнение запросов с 100 параллельными соединениями
        List<Long> eachConnectionTimes = sendRequestsWith100Connections();

        // вывод статистики

        System.out.println("-------------------------------------------------------------------------");
        System.out.println("тест для выборки 1млн записей в 100 потоках завершен");
        double totalTestTime = (System.nanoTime() - startTime) / 1_000_000_000.0;

        System.out.println("Общее время теста в секундах: " + totalTestTime);
        printStatistics(eachConnectionTimes);
    }

    // метод для вставки начальных записей в базу данных перед тестом
    private void insertInitialRows() {
        long startTime = System.nanoTime();
        System.out.println("начинаем вставку " + RECORDS_QUANTITY_TO_INSERT +" записей");

        List<User> users = new ArrayList<>();
        for (int i = 1; i < RECORDS_QUANTITY_TO_INSERT; i++) {
            users.add(new User((long)i, "Name " + i));
        }
        // Сохранение всех записей в базу данных.
        userRepository.saveAll(users);

        double totalInsertTime = (System.nanoTime() - startTime) / 1_000_000_000.0;

        System.out.println("Вставка записей завершена. Время вставки в секундах: " + totalInsertTime);

    }

    // метод для выполнения параллельных запросов с использованием 100 потоков (соединений)
    private List<Long> sendRequestsWith100Connections() throws InterruptedException, ExecutionException {
        // создание пула потоков с 100 соединениями
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CONNECTIONS);
        List<Future<Long>> results = new ArrayList<>();

        System.out.println("начинаем отправку запросов");

        // запуск запросов в отдельных потоках
        for (int i = 0; i < NUM_CONNECTIONS; i++) {
            Callable<Long> task = getConnectionTask();
            results.add(executor.submit(task));
        }

        // сбор результатов времени выполнения всех запросов
        List<Long> times = new ArrayList<>();
        for (Future<Long> result : results) {
            times.add(result.get());
        }
        executor.shutdown();
        return times;
    }

    //Логика для каждого потока (соединения)
    private Callable<Long> getConnectionTask() {
        return () -> {
            //засекаем время
            long totalQueryTime = 0;

            long startTime = System.nanoTime();

            // в каждом потоке будем отправлять один запрос в цикле
            for (int j = 0; j < EACH_CONNECTION_SELECT_QUANTITY + 1; j++) {

                // выбираем случайно id для выборки
                int randomID = ThreadLocalRandom.current().nextInt(1, RECORDS_QUANTITY_TO_INSERT);
                // отправляем GET запрос и маппим в List<User>
                User user = webTestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/users/" + randomID)
                                .build())
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(User.class)
                        .returnResult()
                        .getResponseBody();
                assertThat(user).isNotNull();
                assertThat(user.getName().contains(String.valueOf(randomID))).isTrue();

                if (j % 1000 == 0) {
                    System.out.println("Отправлен запрос № "+ j + " из потока "+ Thread.currentThread().getName() +
                            "; id записи=" + randomID +
                            "; полученное имя: '" + user.getName() + "'");
                }
            }

            totalQueryTime += (System.nanoTime() - startTime);
            return totalQueryTime;
        };
    }

    private static void printStatistics(List<Long> times) {

        // среднее время выполнения запроса
        double averageTime = times.stream().mapToLong(Long::longValue).sum() / (double) NUM_CONNECTIONS;

        // Сортировка времени выполнения для расчета процентилей.
        times.sort(Long::compareTo);

        //медиана, 95 и 99 процентиль
        long medianTime = times.get(times.size() / 2);
        long p95 = times.get((int) (times.size() * 0.95));
        long p99 = times.get((int) (times.size() * 0.99));

        // переводим в секунды
        double averageTimeInSeconds = averageTime / 1_000_000_000.0;
        double medianTimeInSeconds = medianTime / 1_000_000_000.0;
        double p95InSeconds = p95 / 1_000_000_000.0;
        double p99InSeconds = p99 / 1_000_000_000.0;

        System.out.println("Среднее время (sec): " + averageTimeInSeconds);
        System.out.println("Медианное время (sec): " + medianTimeInSeconds);
        System.out.println("95 перцентиль (sec): " + p95InSeconds);
        System.out.println("99 перцентиль (sec): " + p99InSeconds);
    }
}
