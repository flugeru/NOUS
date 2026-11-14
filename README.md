# Vibepollo · Flügel Edition

**Streaming de jogos com foco em baixa latência, estabilidade e diagnóstico reproduzível.**

[![Checks de latência](https://github.com/flugeru/Vibepollo/actions/workflows/latency-checks.yml/badge.svg)](https://github.com/flugeru/Vibepollo/actions/workflows/latency-checks.yml)
[![Build Windows de teste](https://github.com/flugeru/Vibepollo/actions/workflows/fork-test-installer.yml/badge.svg)](https://github.com/flugeru/Vibepollo/actions/workflows/fork-test-installer.yml)
[![Licença GPLv3](https://img.shields.io/badge/licen%C3%A7a-GPLv3-blue.svg)](LICENSE)

Fork independente mantido por [Flügel (@flugeru)](https://github.com/flugeru), baseado no [Vibepollo de Nonary](https://github.com/Nonary/Vibepollo), com origem em [Apollo](https://github.com/ClassicOldSong/Apollo) e [Sunshine](https://github.com/LizardByte/Sunshine).

Esta edição reúne correções pontuais, testes de regressão e ferramentas de análise para streaming entre PC e cliente Moonlight/Artemis. Desenvolvimento assistido por IA, com mudanças documentadas e limites de validação explícitos. Não é uma distribuição oficial dos projetos de origem.

[Melhorias desta edição](#melhorias-desta-edição) · [Downloads e instalação](#downloads-e-instalação) · [Documentação](#documentação) · [Compilação](#compilação) · [Créditos](#créditos-e-licença)

## Visão geral

Vibepollo executa no **PC host**: captura imagem, codifica vídeo e transmite para o cliente. Moonlight ou Artemis executa no dispositivo remoto e cuida da decodificação, apresentação e entrada.

Recursos herdados incluem captura Windows Graphics Capture, displays virtuais, gerenciamento de sessões e dispositivos, integração com bibliotecas de jogos e streaming H.264, HEVC e AV1 conforme suporte do conjunto host/cliente. Recursos específicos como PyroWave e VRR exigem clientes compatíveis; não estão disponíveis automaticamente no Android.

**Foco de validação desta edição:** Windows 11, NVIDIA RTX 5060 e POCO X6 Pro. Esse foco não representa certificação do aparelho nem exclusão das demais plataformas do projeto original.

## Melhorias desta edição

| Área | Alteração |
| --- | --- |
| NVENC | Corrige inflação da janela VBV durante ajustes de bitrate, preservando rollback. |
| Captura Windows | Rejeita mutex abandonado em três caminhos para evitar uso de superfícies inconsistentes. |
| Transporte | Valida limites de `packetSize` RTSP e trata campos SDP vazios. |
| Segurança e robustez | Retira conteúdo de clipboard/nonce dos logs e evita underflow em substituição de payload. |
| WebRTC | Aplica hint de jitter buffer mesmo quando a propriedade existe com valor inicial `null`. |
| Diagnóstico | Adiciona analisador CSV por frame, coletores somente leitura e checks de regressão CPU. |

Defaults de codec, bitrate, preset, multipass, AQ e filas foram preservados. Não há promessa de ganho numérico de latência: testes de lógica não substituem medição no hardware.

Detalhes, fontes e resultados em **[docs/latency/README.md](docs/latency/README.md)**.

## Downloads e instalação

### Windows

Build de teste: **[Fork Test Installer — GitHub Actions](https://github.com/flugeru/Vibepollo/actions/workflows/fork-test-installer.yml)**.

O workflow gera artefatos somente após as etapas de build e validação correspondentes. Abra uma execução bem-sucedida e confira os artefatos; a existência do workflow não garante que o instalador esteja pronto. Artefatos têm retenção de sete dias e o download pelo Actions pode exigir login no GitHub.

> **Build experimental, sem assinatura oficial.** Preserve a configuração e os pareamentos atuais antes de testar. O instalador pode substituir uma instalação existente e alterar serviços, firewall e drivers. Não desative Secure Boot, verificações de assinatura ou outras proteções para fazê-lo funcionar. Builds deste fork não têm a assinatura SignPath do upstream.

### Android

Clientes de origem: [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) e [Artemis](https://github.com/ClassicOldSong/moonlight-android).

As correções listadas acima pertencem ao servidor. Nenhuma otimização nova de MediaCodec foi aplicada ao cliente Android nesta rodada. **APK ARM64 de teste compilado e verificado localmente**, com pacote `com.limelight.vibepollotest` e assinatura Android Debug, separado dos aplicativos oficiais. [Versão, hash, fonte e reconstrução](docs/latency/android-test-build.md). O APK não está publicado em Releases; a entrega local fica em `build/delivery/`.

AV1 é candidato a ensaio, não escolha universal. Decoder efetivo, perfil, bit depth, FPS sustentado e comportamento térmico do POCO ainda precisam ser medidos.

## Estado da validação

- Checks CPU de NVENC, captura, transporte, privacidade, WebRTC e analisador executados; resultados e cobertura no [relatório](docs/latency/README.md#validação-local-desta-rodada).
- [Checks de latência no GitHub Actions confirmados](https://github.com/flugeru/Vibepollo/actions/runs/43627911720) para o commit `94d6bd6`.
- Build Windows completo de teste solicitado em workflow próprio; consulte a execução para o resultado atual.
- Testes no telefone, recuperação física da GPU e latência input-to-photon continuam pendentes.
- Auditoria npm registrou 22 entradas afetadas; triagem e atualização segura permanecem abertas. Suíte frontend local não ficou integralmente verde por ausência de `typescript`.

## Documentação

| Guia | Conteúdo |
| --- | --- |
| [Auditoria e resultados](docs/latency/README.md) | Escopo, correções, testes e limitações. |
| [Arquitetura e transporte](docs/latency/architecture-audit.md) | Filas, pacing, dependências e backlog. |
| [NVENC e captura](docs/latency/nvenc-audit.md) | Rate control, sincronização e evidências. |
| [Android e POCO](docs/latency/decoder-research.md) | MediaCodec, fontes oficiais e capacidades ainda desconhecidas. |
| [APK de teste](docs/latency/android-test-build.md) | Procedência, assinatura, SHA-256 e reconstrução do cliente ARM64. |
| [Benchmark A/B](docs/latency/benchmark.md) | Schema CSV e protocolo de comparação sem misturar relógios. |
| [Compilação](docs/building.md) | Toolchains, dependências e empacotamento. |
| [Linux](docs/linux/install.md) | Instruções herdadas; validação Linux não foi ampliada nesta edição. |

## Compilação

```bash
git clone --recurse-submodules https://github.com/flugeru/Vibepollo.git
cd Vibepollo
```

Siga o [guia de build](docs/building.md) antes de configurar CMake. Windows exige toolchain e pacotes específicos; o ZIP do código-fonte não substitui um clone com submódulos. O [workflow de teste](.github/workflows/fork-test-installer.yml) reutiliza o pipeline Windows existente, sem credenciais de assinatura oficial.

Check independente do analisador, com Python instalado:

```bash
python tools/latency/test_analyze_trace.py
```

## Contribuições e problemas

Relate problemas nas [issues deste fork](https://github.com/flugeru/Vibepollo/issues), incluindo commit, sistema, GPU/driver, versão do cliente e passos de reprodução. Remova dados pessoais, tokens e conteúdo de clipboard dos logs antes de anexar. Consulte [SECURITY.md](SECURITY.md) para o procedimento de segurança herdado; não publique credenciais ou detalhes de exploração em issues públicas.

## Créditos e licença

Manutenção e direção desta edição: **Flügel (@flugeru)**. Trabalho derivado de **Nonary/Vibepollo**, **ClassicOldSong/Apollo**, **LizardByte/Sunshine** e colaboradores. Moonlight, Artemis, bibliotecas e drivers mantêm suas respectivas autorias e licenças.

Código distribuído sob [GNU GPLv3](LICENSE), sujeito aos avisos e às licenças dos componentes em [NOTICE](NOTICE) e `third-party/`. Créditos, avisos legais e histórico upstream são preservados. Patrocínios e certificados do projeto original não se estendem automaticamente a este fork.
