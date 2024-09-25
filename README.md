# Установка, тесты и запуск

## Установка
``git clone https://github.com/ilyazenchenko/high-load-test``

## Тесты 
1. ``cd high-load-test``
2. ``mvn test``

## Запуск
1. ``cd high-load-test``
2. ``mvn install`` (если нужно пропустить тесты, после mvn добавить -DskipTests)
3. ``cd target``
4. ``java -jar nbki-test-0.0.1-SNAPSHOT.jar`` (название jar файла может отличаться)
