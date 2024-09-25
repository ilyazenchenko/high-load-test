# Установка, тесты и запуск

## Установка
git clone https://github.com/ilyazenchenko/high-load-test

## Тесты 
mvn test

## Запуск
1. mvn install (если нужно пропустить тесты, после mvn добавить -DskipTests)
2. cd target
3. java -jar nbki-test-0.0.1-SNAPSHOT.jar (название jar файла может отличаться)
