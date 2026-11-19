<p align="center">
  <img src="branding/NOUS/new-project-icon-transparent.png" alt="Emblema NOUS" width="160" />
</p>

# NOUS

**Seus jogos no PC. Sua tela onde você quiser.**

NOUS conecta um host desktop a um cliente Android para transmitir jogos, desktop e áudio pela rede, com controles por toque, teclado, mouse ou gamepad. Mantido por [Flügel (@flugeru)](https://github.com/flugeru/NOUS), a partir do ecossistema Sunshine, Apollo e Moonlight.

[Começar](#começar) · [Android](android/README.md) · [Compilar](docs/building.md) · [Segurança](SECURITY.md) · [Publicação](docs/maintainers/release.md#android--google-play)

> **Em desenvolvimento.** A versão pública do projeto é `1.0.0`. APKs de debug e instaladores locais sem assinatura são builds de teste, não releases de produção. Este README não anuncia disponibilidade nem aprovação na Google Play. Consulte os arquivos e as notas de cada [release](https://github.com/flugeru/NOUS/releases).

## Dois aplicativos, uma sessão

| Componente | Função |
| --- | --- |
| **Host NOUS** | Captura e codifica o PC, transmite vídeo/áudio e recebe entrada do cliente. |
| **NOUS Desktop** | Janela Windows de administração, separada do serviço do host. Fechá-la não encerra a transmissão. |
| **NOUS Android** | Descobre PCs, abre a biblioteca, recebe a transmissão e envia controles. |
| **Painel Web** | Administra aplicativos, pareamentos, permissões, configurações e diagnósticos. Streaming pelo navegador está pausado na interface. |

### Recursos

- H.264, HEVC e AV1, conforme suporte do host e do cliente.
- Biblioteca com busca, categorias, capas e atalhos de launchers.
- Perfis de vídeo, áudio e controles no Android; menu rápido durante a sessão.
- Pareamento local por PIN e fluxo QR LAN em teste; uso local sem conta.
- Google opcional para descoberta de PCs e autorização por conta em teste. Login de conta não substitui a senha administrativa do host.
- Histórico local e diagnósticos com retenção limitada e exportação filtrada.

HDR, codecs, monitores virtuais e controles variam por plataforma, driver e hardware. Emulador não comprova desempenho ou compatibilidade de um telefone real. Discord, mescla de contas e convites entre contas não são recursos concluídos.

## Começar

1. Obtenha um build compatível do host e instale-o em uma pasta dedicada. No Windows, use `C:\Program Files\NOUS`, não a pasta do Windows nem a raiz de uma unidade.
2. Instale o cliente Android correspondente. Confira origem, versão e assinatura; não desative proteções do sistema para instalar drivers.
3. Coloque PC e Android na mesma rede. Se a descoberta falhar, adicione o endereço local do PC manualmente.
4. Faça o pareamento e confirme o acesso no host. Escolha **Desktop**, um jogo ou um launcher na biblioteca.

Comece com uma rede estável e configurações moderadas. Não exponha o painel administrativo à internet. Antes de atualizar, faça backup protegido das configurações e dos pareamentos. Certificados e tokens do backup são privados.

O host pode exigir permissões para captura, firewall e serviço. Drivers opcionais devem ser instalados apenas quando necessários e verificados. Problemas de driver exigem um ambiente de teste recuperável, não repetição no computador de trabalho.

## Compilar e testar

Clone o repositório e selecione a revisão que pretende compilar:

```sh
git clone --recurse-submodules https://github.com/flugeru/NOUS.git
cd NOUS
```

### Android

Requisitos: JDK 17, Android SDK Platform 36 e NDK `27.0.12077973`. Configure `JAVA_HOME` e `ANDROID_HOME`, ou `android/local.properties` localmente.

```powershell
cd android
.\gradlew.bat --no-daemon :app:assembleNonRoot_gameDebug :app:testNonRoot_gameDebugUnitTest
```

APK de desenvolvimento: `android/app/build/outputs/apk/nonRoot_game/debug/`. Pacote: `com.flugel.nous`. Debug não serve para publicação na loja. Veja [instruções Android](android/README.md) e [checklist Google Play](docs/maintainers/release.md#android--google-play).

### Windows

Configure toolchain e dependências seguindo [docs/building.md](docs/building.md). Para gerar o instalador completo no diretório de build já configurado:

```sh
cmake --build build --target package_installer --parallel 6
```

O alvo prepara MSI e setup; Inno Setup deve estar configurado. O alvo `package_msi` gera apenas o MSI. Não execute CPack isoladamente como substituto dos alvos de empacotamento. Build local não instala o produto nem publica uma release.

### Web e documentação pública

```sh
cd src_assets/common/assets/web
npm ci
npm run test:unit
npm run build
```

Na raiz, confira a higiene dos documentos e as regras de exclusão antes de publicar:

```sh
python tools/test_public_docs.py
```

## Documentação e suporte

| Documento | Conteúdo |
| --- | --- |
| [Primeiros passos](docs/getting_started.md) | Configuração e uso do host. |
| [Compilação](docs/building.md) | Toolchains, dependências e empacotamento. |
| [Cliente Android](android/README.md) | Build, configuração opcional de conta e distribuição. |
| [Conta e dispositivos](docs/nous-conta-dispositivos-desktop.md) | Fluxos de autorização e limites atuais. |
| [Pesquisa de latência](docs/latency/README.md) | Evidências, benchmark e limites dos testes. |
| [Pendências e auditoria](docs/nous-entrega-parcial-2026-11-18.md) | Registro histórico de testes e trabalho restante. |
| [Manutenção e release](docs/maintainers/README.md) | Validação, assinatura e preparação para distribuição. |

Relate bugs em [Issues](https://github.com/flugeru/NOUS/issues), com versão, sistema, hardware relevante e passos de reprodução. Não publique senhas, tokens, chaves, nomes de PCs, IPs reais, screenshots privados ou dumps completos. Vulnerabilidades devem seguir a [política de segurança](SECURITY.md).

## Créditos e licença

NOUS é trabalho derivado, não distribuição oficial dos projetos de origem:

- [Nonary/Vibepollo](https://github.com/Nonary/Vibepollo), base do host desta edição.
- [ClassicOldSong/Apollo](https://github.com/ClassicOldSong/Apollo) e [LizardByte/Sunshine](https://github.com/LizardByte/Sunshine), origem do host e contribuições anteriores.
- [Artemis](https://github.com/ClassicOldSong/moonlight-android), base Android importada da revisão [`c5cf27f4dc822db0e863c4691e7a70c74bea977a`](https://github.com/ClassicOldSong/moonlight-android/tree/c5cf27f4dc822db0e863c4691e7a70c74bea977a).
- [Moonlight](https://github.com/moonlight-stream), bibliotecas e contribuições do ecossistema GameStream.
- Componentes em [third-party/](third-party/) e [NOTICE](NOTICE).

Direção e manutenção: **Flügel**. Desenvolvimento assistido por IA. Autorias, avisos legais, licenças e histórico upstream são preservados. Identificadores internos herdados permanecem por compatibilidade; não representam a marca do produto.

Código sob [GNU GPLv3](LICENSE), respeitando as licenças específicas dos componentes. Redistribuição, inclusive pela Google Play, deve cumprir essas licenças e disponibilizar o código-fonte correspondente. Assinaturas, garantias e patrocínios dos projetos de origem não se estendem automaticamente ao NOUS.
