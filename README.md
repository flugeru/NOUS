<p align="center">
  <img src="branding/NOUS/new-project-icon-transparent.png" alt="Emblema NOUS" width="180" />
</p>

# NOUS

**Seus jogos no PC. Sua tela onde você quiser.**

NOUS reúne host desktop e cliente Android para streaming de jogos pela rede. Projeto mantido por [Flügel (@flugeru)](https://github.com/flugeru), com foco em baixa latência, controle da sessão e diagnóstico reproduzível.

**Versão:** 1.0.0 · **Branch principal:** [`nous-project`](https://github.com/flugeru/NOUS/tree/nous-project) · **Licença:** [GPLv3](LICENSE)

## Dois aplicativos, um projeto

| Aplicativo | Função | Código |
| --- | --- | --- |
| **NOUS Desktop** | Captura e codifica o PC, recebe controles e transmite a sessão. | [`src/`](src/), [`CMakeLists.txt`](CMakeLists.txt) |
| **NOUS Android** | Recebe vídeo e áudio, apresenta a imagem e envia comandos ao PC. | [`android/`](android/) |

Desktop usa emblema **transparente**. Android usa emblema **com fundo preto**, inclusive no launcher adaptativo. Arquivos de referência ficam em [`branding/NOUS/`](branding/NOUS/).

## Recursos

- Streaming H.264, HEVC e AV1, conforme suporte de hardware e cliente.
- Captura de desktop, integração com bibliotecas de jogos e suporte a displays virtuais no Windows.
- Pareamento, biblioteca com busca, perfis e configurações de vídeo, áudio e controles no Android.
- Interface web para administrar host, aplicativos e dispositivos.
- Ferramentas para analisar traces por frame e verificar regressões na lógica de captura, codificação e transporte.

Alguns recursos herdados dependem de clientes ou drivers específicos. Disponibilidade de codec, HDR, display virtual e controle varia conforme sistema e hardware. Não há promessa de latência numérica sem medição ponta a ponta.

## Começar

1. Compile ou obtenha build NOUS Desktop e instale no PC host.
2. Instale NOUS Android no dispositivo remoto.
3. Mantenha ambos na mesma rede para primeiro pareamento. Adicione endereço do PC quando descoberta automática não funcionar.
4. Confirme PIN no host e escolha jogo ou desktop no cliente.

> **Builds experimentais.** Faça backup das configurações e dos pareamentos antes de instalar. Instalador Windows pode alterar serviços, firewall e drivers. Builds locais não possuem assinatura oficial de distribuição. Não desative Secure Boot nem verificações de assinatura para instalar drivers.

## Compilação local

Build local é fluxo padrão. GitHub Actions fica reservado para execuções autorizadas; commit não significa publicação de release.

```sh
git clone --branch nous-project --recurse-submodules https://github.com/flugeru/NOUS.git NOUS
cd NOUS
```

### Android

Requisitos: JDK 17, Android SDK Platform 36 e NDK `27.0.12077973`. Defina `JAVA_HOME` e `ANDROID_HOME`, ou informe SDK em `android/local.properties` sem versionar caminhos pessoais.

PowerShell, a partir da raiz:

```powershell
git submodule update --init --recursive -- android/app/src/main/jni/moonlight-core/moonlight-common-c
cd android
.\gradlew.bat --no-daemon :app:assembleNonRoot_gameDebug
.\gradlew.bat --no-daemon :app:testNonRoot_gameDebugUnitTest
```

APK ARM64 de debug fica em `android/app/build/outputs/apk/nonRoot_game/debug/`. Identidade Android: `com.flugel.nous`. Assinatura Debug serve para desenvolvimento, não para publicação em loja.

### Windows

Requisitos principais: MSYS2 UCRT64 com GCC, CMake e Ninja; Visual Studio Build Tools e Windows SDK; Node.js 22; .NET SDK 10; WiX Toolset 3.14. Dependências e pacotes de drivers usam versões fixadas pelo projeto.

Siga [`docs/building.md`](docs/building.md) para configurar dependências e CMake. Depois, no ambiente UCRT64 configurado:

```sh
cmake --build build --target all --parallel 6
cmake --build build --target package_msi --parallel 6
```

Use alvo `package_msi`, não CPack isoladamente: alvo prepara e valida pacotes necessários antes do instalador. EXE de instalação é gerado pelo script [`build_bootstrapper.ps1`](packaging/windows/bootstrapper/build_bootstrapper.ps1), com MSI como entrada. Não versionar caches, SDKs, credenciais nem artefatos compilados.

## Desenvolvimento e validação

Código local e branch `nous-project` devem permanecer sincronizados após mudanças verificadas. Trabalho não concluído ou não testado deve ser identificado, sem sobrescrever alterações de terceiros.

Validação de lógica não substitui streaming real. Desempenho no telefone, comportamento térmico, recuperação da GPU e latência input-to-photon exigem ensaio no hardware.

```sh
python tools/latency/test_analyze_trace.py
```

| Documento | Conteúdo |
| --- | --- |
| [Compilação](docs/building.md) | Toolchains, dependências e empacotamento. |
| [Auditoria](docs/latency/README.md) | Correções, evidências e limites dos testes. |
| [Benchmark](docs/latency/benchmark.md) | Protocolo A/B e formato dos traces. |
| [Android e decodificação](docs/latency/decoder-research.md) | Pesquisa técnica e medições pendentes. |
| [Segurança](SECURITY.md) | Orientações para relatar problemas sensíveis. |

Relate bugs em [Issues](https://github.com/flugeru/NOUS/issues). Inclua commit, sistema, GPU/driver, cliente e passos para reproduzir. Remova tokens, senhas e dados pessoais dos logs.

## Créditos e origem

NOUS é trabalho derivado, não distribuição oficial dos projetos de origem:

- **[Nonary/Vibepollo](https://github.com/Nonary/Vibepollo)** — base do host desta edição.
- **[ClassicOldSong/Apollo](https://github.com/ClassicOldSong/Apollo)** e **[LizardByte/Sunshine](https://github.com/LizardByte/Sunshine)** — origem do host e contribuições anteriores.
- **[Artemis](https://github.com/ClassicOldSong/moonlight-android)** — base do cliente Android, importada da revisão [`c5cf27f4dc822db0e863c4691e7a70c74bea977a`](https://github.com/ClassicOldSong/moonlight-android/tree/c5cf27f4dc822db0e863c4691e7a70c74bea977a).
- **[Moonlight](https://github.com/moonlight-stream)** e seus colaboradores — clientes e bibliotecas do ecossistema GameStream.
- Autores das bibliotecas, drivers e demais componentes listados em [`third-party/`](third-party/) e [`NOTICE`](NOTICE).

Direção e manutenção NOUS: **Flügel**. Desenvolvimento assistido por IA. Autorias, avisos legais, licenças e histórico upstream permanecem preservados. Alguns identificadores internos continuam com nomes de origem por compatibilidade de protocolo, JNI, drivers ou migração; não representam marca do produto.

Código sob [GNU GPLv3](LICENSE), respeitando licenças específicas dos componentes. Certificados, patrocínios e garantias dos projetos de origem não se estendem automaticamente ao NOUS.
