# NOUS Android

Cliente do [NOUS](../README.md) para transmitir jogos, desktop e áudio de um PC. Interface escura Material 3, ações rosa, navegação ciano e suporte a toque, teclado, mouse e gamepad, conforme hardware e permissões.

## Estado e identidade

- Versão pública: `1.0.0`. O `versionCode` identifica a revisão técnica.
- Pacote principal: `com.flugel.nous`; variante legada root: `com.flugel.nous.root`.
- Android mínimo: API 24. `compileSdk` e `targetSdk` atuais: 36.
- Build padrão: arm64-v8a. `-PnousEmulator` seleciona x86_64 para desenvolvimento.
- Namespace `com.limelight` e identificadores JNI são mantidos por compatibilidade.
- APK debug é para teste; não é o artefato a enviar à Google Play. Não há anúncio de publicação na loja nesta documentação.

## Uso

1. Instale e configure o host no PC. Conecte os aparelhos à mesma rede no primeiro teste.
2. Abra **Computadores**, selecione o PC ou adicione seu endereço local.
3. Confirme o pareamento no host. A conta Google é opcional e não concede acesso administrativo ao PC.
4. Abra a biblioteca e inicie um aplicativo. Durante o vídeo, use a alça ou o botão configurado para abrir o menu rápido.

**Desconectar** sai da transmissão sem pedir o encerramento do aplicativo no host. **Encerrar sessão** pede confirmação antes de fechar o aplicativo remoto. Não use a segunda ação com progresso não salvo.

## Compilar e testar

Requisitos: JDK 17, Android SDK Platform 36 e NDK `27.0.12077973`. Defina `JAVA_HOME` e `ANDROID_HOME`, ou use `local.properties`, que não deve ser versionado.

Na raiz do repositório:

```sh
git submodule update --init --recursive -- android/app/src/main/jni/moonlight-core/moonlight-common-c
```

Em `android/`, PowerShell:

```powershell
.\gradlew.bat --no-daemon :app:assembleNonRoot_gameDebug :app:testNonRoot_gameDebugUnitTest
```

Saída: `app/build/outputs/apk/nonRoot_game/debug/`. Para atualizar um aparelho de teste sem limpar dados:

```sh
adb -s <serial-do-aparelho> install -r <arquivo.apk>
```

A assinatura deve ser compatível com a versão instalada. Não desinstale nem limpe dados para contornar uma incompatibilidade sem antes preservar pareamentos e preferências.

## Conta Google opcional

Um build sem configuração Firebase conserva o modo local. Para habilitar a integração no seu ambiente, use o `google-services.json` do projeto autorizado em `app/`; esse arquivo fica fora do Git. Restrinja as chaves públicas e cadastre pacote e certificados corretos no provedor.

IDs de cliente e configuração pública Firebase não são segredos de servidor. Chaves privadas, credenciais de conta de serviço, segredos OAuth e tokens de usuário **nunca** devem ser incluídos no APK ou no repositório. O broker Desktop é separado; não copie sua credencial para o Android.

O certificado de assinatura distribuído pelo Play App Signing pode ser diferente do certificado de upload e do debug. Configure a integração para o certificado efetivamente usado no app entregue, sem alterar a identidade do pacote.

## Preparação para Google Play

Gere o bundle de release, ainda sem afirmar que está assinado ou pronto para envio:

```powershell
.\gradlew.bat --no-daemon :app:bundleNonRoot_gameRelease
```

A assinatura de release não está configurada neste projeto. Configure uma chave de upload fora do repositório e verifique o AAB antes de enviar. Não use a chave debug. Siga o [checklist de publicação](../docs/maintainers/release.md#android--google-play): Play App Signing, política de privacidade, Segurança dos dados, exclusão de conta quando aplicável, requisitos atuais de API e bibliotecas nativas com páginas de 16 KB.

O projeto solicita páginas flexíveis no NDK; isso não comprova que todas as bibliotecas `.so` do artefato final sejam compatíveis. Teste bundle, splits e instalação pelo canal interno da loja.

## Validação e limites

Testes unitários, renderizações e MuMu não substituem testes físicos de controle, decoder, áudio, rotação, temperatura, consumo de bateria, acessibilidade e desempenho. Warp é experimental; não prometa ganhos sem medir a sessão real.

[Configuração dos testes](android_test_setup.md) · [Ferramentas](tools/) · [Benchmark](../docs/latency/README.md) · [Segurança](../SECURITY.md)

## Créditos e licença

Derivado de [Artemis](https://github.com/ClassicOldSong/moonlight-android), revisão `c5cf27f4dc822db0e863c4691e7a70c74bea977a`, baseado em [Moonlight Android](https://github.com/moonlight-stream/moonlight-android). Consulte [créditos completos](../README.md#créditos-e-licença), [LICENSE](../LICENSE) e [NOTICE](../NOTICE). Preserve avisos e disponibilize o código-fonte correspondente aos binários distribuídos.
