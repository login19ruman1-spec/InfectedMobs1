# Загрузка проекта в GitHub

Содержимое этого ZIP нужно загружать **в корень репозитория**, не внутрь дополнительной папки.

В корне GitHub должны находиться:
- `build.gradle`
- `settings.gradle`
- `.github/workflows/build.yml`
- `src/main/java/...`
- `src/main/resources/...`

После загрузки открой **Actions → Build InfectedMobs**. Workflow сам установит Java 21 и Gradle 8.10.2 и соберёт JAR.

В этой версии `setup-java@v5` не использует `cache: gradle`, поэтому ошибка `No file ... matched to ...` от setup-java больше не возникает.
