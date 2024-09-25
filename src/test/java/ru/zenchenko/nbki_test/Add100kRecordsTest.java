package ru.zenchenko.nbki_test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.zenchenko.nbki_test.model.User;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class Add100kRecordsTest {

    @Autowired
    private WebTestClient webTestClient;

    // количество записей для первого теста
    private static final int INSERT_TEST_RECORDS = 100_000;

    // ограничиваем время 90 секундами
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    public void testAdd100kRecords() {
        System.out.println("-------------------------------------------------------------------------");
        System.out.println("Начинаем тест для добавления 100 000 записей");

        long startTime = System.nanoTime();

        // с помощью POST-запросов вставляем 100000 записей
        IntStream.range(0, INSERT_TEST_RECORDS).forEach(i -> {
            webTestClient.post()
                    .uri("/users")
                    .bodyValue(new User("Name " + i))
                    .exchange()
                    .expectStatus().isCreated();
            if (i % 10000 == 0) System.out.println("отправлено запросов и добавлено записей: " + i);
        });

        // считаем итоговое кол-во строк
        Long countResponse = webTestClient.get()
                .uri("/users/count")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .returnResult()
                .getResponseBody();

        assertThat(countResponse).isEqualTo(INSERT_TEST_RECORDS);

        System.out.println("-------------------------------------------------------------------------");
        System.out.println("тест для добавления 100 000 записей завершен успешно");

        double totalTestTime = (System.nanoTime() - startTime) / 1_000_000_000.0;
        System.out.println("общее время в секундах: " + totalTestTime);
    }

}
