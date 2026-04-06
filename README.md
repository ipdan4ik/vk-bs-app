# Lumus VK VPN

Android-приложение, которое объединяет WireGuard tunnel/backend и локальный `vk-turn` transport для автоматизированного подключения через VK/Yandex call links.

## Environment

- Build JDK: `21`
- Android SDK: `36`
- Min SDK: `24`
- Supported proxy binary ABI right now: `arm64-v8a`

Почему именно `JDK 21`:

- системный `JDK 26` в этой среде ломал Android/Gradle toolchain;
- проект проверен на `Gradle 9.3.1` + `JDK 21`;
- сборка теперь специально падает с понятной ошибкой, если запущена не под `JDK 21`.

В проекте уже зафиксированы:

- [.java-version](/home/ipu/code/work/lumusvpn/vk-app/.java-version)
- [gradle/gradle-daemon-jvm.properties](/home/ipu/code/work/lumusvpn/vk-app/gradle/gradle-daemon-jvm.properties)

## Android Studio

1. Open the project root.
2. Set `Gradle JDK = 21`.
3. Ensure `Android SDK Platform 36` is installed.
4. Run the `app` configuration on an `arm64` device or emulator.

## CLI

If JDK 21 is installed locally:

```bash
cd /home/ipu/code/work/lumusvpn/vk-app
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew testDebugUnitTest
./gradlew installDebug
```

If you use the temporary JDK downloaded during setup in this environment:

```bash
cd /home/ipu/code/work/lumusvpn/vk-app
export JAVA_HOME=/tmp/jdk21
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/tmp/gradle
./gradlew testDebugUnitTest
./gradlew installDebug
```

## Notes

- `local.properties` currently points to `/home/ipu/Android/Sdk` in this environment.
- `android.disallowKotlinSourceSets=false` is enabled as a transitional AGP/KSP compatibility flag.
- Unit tests currently pass with `./gradlew --no-daemon testDebugUnitTest` under `JDK 21`.
