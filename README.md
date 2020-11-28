Код семинар по теме "Потоковая обработка данных" в курсе MADE.

На семинаре был разобран алгоритм многоруких бандитов UCB1.

Реализовано три джобы:

1. GeneratorJob -- генерит поток запросов от пользователей, генерит ответ пользователя на предсказания из PredictJob
2. PredictJob -- получает запрос из GeneratorJob и счетчики по айтемам из UpdateJob, делает сортировку айтемов по UCB1
3. UpdateJob -- считает количество показов и кликов по одному айтему в сессии

Сборка дистрибутива

```
sbt clean assembly
``` 

Собираем docker image

```
docker build -t flink-jobs .
```

Запускаем 

```
docker-compose up -d --scale taskmanager=3
```
