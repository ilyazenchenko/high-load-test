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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NbkiTestApplicationTests {

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private UserRepository userRepository;

	// количество записей для первого теста
	private static final int INSERT_TEST_RECORDS = 100_000;

	// ограничиваем время 90 секундами
	@Test
	@Timeout(value = 90, unit = TimeUnit.SECONDS)
	public void testAdd100kRecords() {
		System.out.println("-------------------------------------------------------------------------");
		System.out.println("начинаем тест для добавления 100 000 записей");

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

	//--------------------------------------------------------------------
	// константы для второго теста

	//100 соединений
	private static final int NUM_CONNECTIONS = 100;

	//каждое соединение выбирает 10_000 записей одним запросом, итого получается миллион
	private static final int RECORDS_RANGE_TO_SELECT = 10_000;

	//заполним изначально бд таким кол-вом записей
	private static final int SELECT_TEST_RECORDS_TO_INSERT = 100_000;

	// Тест для выборки 1 миллиона записей через 100 параллельных соединений.
	// ограничение в 60 секунд
	@Test
	@Timeout(value = 60, unit = TimeUnit.SECONDS)
	public void testSelect1MRecordsWith100Connections() throws InterruptedException, ExecutionException {
		System.out.println("-------------------------------------------------------------------------");
		System.out.println("начинаем тест для выборки 1млн записей в 100 потоках (по 10000 на каждый)");

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

		System.out.println("Общее время в секундах: " + totalTestTime);
		printStatistics(eachConnectionTimes, totalTestTime);
	}

	// метод для вставки начальных записей в базу данных перед тестом
	private void insertInitialRows() {
		List<User> users = new ArrayList<>();
		for (int i = 1; i < SELECT_TEST_RECORDS_TO_INSERT; i++) {
			users.add(new User((long)i, "Name " + i));
		}
		// Сохранение всех записей в базу данных.
		userRepository.saveAll(users);
	}

	// метод для выполнения параллельных запросов с использованием 100 потоков (соединений)
	private List<Long> sendRequestsWith100Connections() throws InterruptedException, ExecutionException {
		// создание пула потоков с 100 соединениями
		ExecutorService executor = Executors.newFixedThreadPool(NUM_CONNECTIONS);
		List<Future<Long>> results = new ArrayList<>();

		// запуск запросов в отдельных потоках
		for (int i = 0; i < NUM_CONNECTIONS; i++) {
			results.add(executor.submit(() -> {
				//засекаем время
				long totalQueryTime = 0;

				// выбираем случайно начальный id для выборки, выберем 10000 записей после него
				int randomID = ThreadLocalRandom.current().nextInt(1, SELECT_TEST_RECORDS_TO_INSERT - RECORDS_RANGE_TO_SELECT);
				long startTime = System.nanoTime();

				// отправляем GET запрос и маппим в List<User>
				List<User> usersResult = webTestClient.get()
						.uri(uriBuilder -> uriBuilder
								.path("/users/range")
								.queryParam("start", randomID)
								.queryParam("range", RECORDS_RANGE_TO_SELECT)
								.build())
						.exchange()
						.expectStatus().isOk()
						.expectBody(new ParameterizedTypeReference<List<User>>() {})
						.returnResult()
						.getResponseBody();

				System.out.println("Отправлен запрос из потока "+ Thread.currentThread().getName() + "; начальный id=" + randomID);

				totalQueryTime += (System.nanoTime() - startTime);
				return totalQueryTime;
			}));
		}

		// сбор результатов времени выполнения всех запросов
		List<Long> times = new ArrayList<>();
		for (Future<Long> result : results) {
			times.add(result.get());
		}
		executor.shutdown();
		return times;
	}

	private static void printStatistics(List<Long> times, double totalTestTime) {
		//  общее время выполнения всех запросов
		long totalTime = times.stream().mapToLong(Long::longValue).sum();

		// среднее время выполнения запроса
		double averageTime = totalTime / (double) NUM_CONNECTIONS;

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

		System.out.println("Average time (sec): " + averageTimeInSeconds);
		System.out.println("Median time (sec): " + medianTimeInSeconds);
		System.out.println("95th percentile (sec): " + p95InSeconds);
		System.out.println("99th percentile (sec): " + p99InSeconds);
	}

}
