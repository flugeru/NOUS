# NOUS Android

Cliente Android do [NOUS](../README.md). Recebe streaming do host, exibe biblioteca, gerencia perfis e envia controles ao PC.

## Identidade

- Aplicativo: **NOUS**, versão **1.0.0**.
- Pacote principal: `com.flugel.nous`; variante root: `com.flugel.nous.root`.
- Launcher e imagens Android usam **fundo preto**. Desktop usa versão transparente.
- Namespace Java `com.limelight` e identificadores JNI permanecem para preservar integração nativa.

## Compilar

Requisitos: JDK 17, Android SDK Platform 36 e NDK `27.0.12077973`.

Na raiz do repositório:

```sh
git submodule update --init --recursive -- android/app/src/main/jni/moonlight-core/moonlight-common-c
```

No diretório `android/`, PowerShell:

```powershell
.\gradlew.bat --no-daemon :app:assembleNonRoot_gameDebug
.\gradlew.bat --no-daemon :app:testNonRoot_gameDebugUnitTest
```

APK: `app/build/outputs/apk/nonRoot_game/debug/`. Para x86_64 de emulador, acrescente `-PnousEmulator`. SDK pode ser informado por `ANDROID_HOME` ou `local.properties`, sem versionar caminhos pessoais.

Debug APK usa chave de desenvolvimento. Não substitui assinatura de release. Valide decodificação, entrada, áudio, rotação e temperatura no dispositivo real antes de atribuir ganhos de desempenho.

## Recursos

Interface Material 3, navegação entre computadores/biblioteca/configurações, busca, pareamento e perfis. Recursos herdados incluem controles virtuais, modos de mouse, monitor externo, ajustes de resolução/bitrate e integração com displays virtuais em hosts compatíveis.

[Configuração dos testes](android_test_setup.md) · [Ferramentas](tools/) · [Pesquisa e benchmark](../docs/latency/README.md)

## Créditos e licença

Cliente derivado de [Artemis](https://github.com/ClassicOldSong/moonlight-android), revisão `c5cf27f4dc822db0e863c4691e7a70c74bea977a`, baseado em [Moonlight Android](https://github.com/moonlight-stream/moonlight-android). Autorias e licenças das bibliotecas nativas permanecem preservadas. Consulte [créditos completos](../README.md#créditos-e-origem), [LICENSE](../LICENSE) e [NOTICE](../NOTICE).
