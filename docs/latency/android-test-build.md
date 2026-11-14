# APK Artemis de teste — Flügel Edition

Build local criado em 2026-11-14 para inspeção. Não é release oficial de Artemis/Moonlight, nem contém alterações novas no decoder. Correções de NVENC, captura e transporte desta edição ficam no servidor Windows.

| Campo | Valor |
| --- | --- |
| Fonte | https://github.com/ClassicOldSong/moonlight-android |
| Commit | `c5cf27f4dc822db0e863c4691e7a70c74bea977a` |
| Versão | `20.2.6`, versionCode `57` |
| Variante | `nonRoot_gameDebug` |
| Arquitetura | `arm64-v8a` |
| Application ID | `com.limelight.vibepollotest` |
| Nome exibido | `Artemis Vibepollo Test` |
| Assinatura | Android Debug, não oficial |
| Arquivo | `Artemis-Vibepollo-Test-20.2.6-arm64-v8a-debug.apk` |
| Tamanho | `41410477` bytes |
| SHA-256 | `85ce7a157dd59e759e7ea59bd666f72ab7b59dd9cd728f7d94e23406a6ed884e` |

Arquivo entregue localmente em `build/delivery/`, ignorado pelo Git. Não há link público de download do APK neste documento. Pacote separado permite coexistir com clientes oficiais; não transfere automaticamente configurações ou pareamentos.

## Alterações de build

[Patch completo](artemis-test.patch): identidade/nome próprios na variante debug, somente ABI ARM64 e SHA-256 oficial da distribuição Gradle. Nenhuma alteração de codec, filas, vendor keys, pacing ou comportamento de rede. A configuração release original permanece intacta.

## Reconstrução

1. Clone a fonte, selecione o commit exato e inicialize os submódulos recursivos.
2. Aplique `artemis-test.patch` com `git apply` na raiz do cliente.
3. Prepare JDK 17, Android SDK 36 e NDK `27.0.12077973`; configure `JAVA_HOME` e `ANDROID_HOME` para suas instalações. O Android SDK requer aceite dos termos aplicáveis.
4. Execute na raiz do cliente, no Windows:

   ```powershell
   .\gradlew.bat :app:assembleNonRoot_gameDebug --console=plain --stacktrace --no-daemon
   ```

5. Confira o APK em `app/build/outputs/apk/` com `apksigner verify --verbose --print-certs`, `aapt dump badging` e `zipalign -c -v 4`, usando os binários do Android Build Tools.

Gradle 8.13 é validado pelo hash fixado no patch. Dependências Maven/JitPack e submódulos seguem a revisão do cliente. Assinatura debug e disponibilidade das dependências impedem prometer reprodução byte a byte.

## Validação executada

- Gradle concluiu a montagem do APK.
- `apksigner` retornou código zero; assinaturas v1/v2 válidas, certificado Android Debug. Avisos de entradas META-INF da assinatura v1 constam no log preservado.
- `aapt` confirmou pacote, rótulo, versão e ABI.
- ZIP contém cinco bibliotecas nativas, todas sob `lib/arm64-v8a/`.
- `zipalign` passou; patch e whitespace foram verificados.

Nenhum aparelho foi acessado e nenhum APK foi instalado. Compatibilidade, desempenho sustentado, térmica, latência e qualidade visual no POCO continuam sem medição. Uma primeira tentativa de build com override de política PowerShell foi interrompida; a execução concluída usou comandos explícitos, sem override. Registro dessa ocorrência preservado junto dos artefatos locais.

## Licença e procedência

Código do cliente e componentes mantêm suas licenças e créditos originais. Para obter o código correspondente a este APK, use a revisão acima, seus submódulos fixados e o patch fornecido; preserve os avisos e consulte o arquivo de licença do repositório de origem. Chave privada de assinatura não é publicada. Não trate assinatura de teste como identidade oficial nem reduza proteções do Android para instalar um pacote rejeitado.
