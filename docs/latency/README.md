# Projeto de baixa latência: RTX 5060 → POCO X6 Pro

Primeira auditoria: 2026-11-14. Fork: https://github.com/flugeru/Vibepollo. Base upstream: `5e6ed8594184212d429bb093d7fd47904c9a6fa6` de `Nonary/Vibepollo`, branch `master`.

**Estado honesto:** correções de lógica e testes CPU, pesquisa técnica e protocolo de medição. Não há benchmark ponta a ponta, nem certificação de compatibilidade do telefone, nem build Windows completo nesta entrega. “Auditoria completa” não significa todas as linhas, dependências, drivers e caminhos de runtime certificados; cobertura e lacunas abaixo.

## Mapa da investigação

| Documento | Conteúdo |
| --- | --- |
| [Arquitetura e transporte](architecture-audit.md) | componentes, filas, pacing, configurações, dependências, segurança/CI e riscos |
| [NVENC e captura Windows](nvenc-audit.md) | bugs reproduzidos, VBV, sincronização, recursos/presets, captura e testes |
| [Decoder POCO / Android](decoder-research.md) | fontes Xiaomi/MediaTek/Android/NVIDIA, análise de clientes fixada por SHA, capacidades desconhecidas |
| [Benchmark e diagnóstico](benchmark.md) | métricas por frame, clocks separados, ensaio A/B e input-to-photon |
| [Hints WebRTC](webrtc-hints.md) | propriedade nullable do jitter buffer, correção e 14 testes sem navegador |

## Decisões já fundamentadas

- Vibepollo é **servidor**. Decoder MediaCodec e pacing de apresentação Android ficam no APK Moonlight/Artemis. Copiar opções Android para configuração do host não otimiza decoder.
- RTX 5060 desktop: fabricante anuncia **um NVENC de nona geração**, H.264/HEVC/AV1. Split-frame entre vários NVENCs não é caminho útil neste modelo.
- Inventário local somente leitura confirmou `NVIDIA GeForce RTX 5060`, driver `621.24`, `8151 MiB`. Isso não consulta recursos da sessão NVENC nem mede desempenho.
- MediaTek anuncia AV1 e “4K AV1 media decoding” para Dimensity 8300; POCO usa 8300-Ultra. Não há evidência do aparelho para componente, perfil, bit depth, fps sustentado ou low-latency. Tela 120 Hz não prova decode 120 FPS.
- AV1 merece ensaio por eficiência; HEVC permanece candidato real. “Codec mais novo”, “preset máximo” e menor tempo médio de decode não escolhem automaticamente melhor solução.
- Defaults de codec, preset, bitrate, multipass, AQ, captura e profundidade das filas foram preservados. Sem hacks de registro, desativação de segurança, realtime priority ou alteração de driver/serviço.

## Melhorias e grau de comprovação

### VBV durante mudança de bitrate

Removido piso arbitrário de 100 kbit que podia inflar janela VBV e perpetuar inflação nos ajustes seguintes. Teste extrai método real e inspeciona parâmetros de driver simulado; baseline falha e patch passa. Exemplo 12 Mbps/240 FPS reduzido para 2 Mbps: 100.000 bits antigos versus 8.333 bits corrigidos. **50 ms versus 4,17 ms são janelas nominais de rate control, não redução medida de latência.** Guarda mínima positiva evita pedir default do driver; rollback preservado. [Detalhes](nvenc-audit.md).

### Sincronização de captura

Três caminhos deixaram de tratar `WAIT_ABANDONED` como aquisição válida de `IDXGIKeyedMutex`. WGC solicita reinit; conversão/helper falham antes de ler/copiar/liberar superfície inconsistente. Teste CPU passa e reintroduções dos três defeitos falham. Lifecycle e limites em [NVENC e captura](nvenc-audit.md). Resultado do fluxo de controle não comprova recuperação real após device loss; essa validação continua pendente.

### Transporte e privacidade

Parser RTSP agora rejeita tamanhos de pacote fora do intervalo já definido pelo projeto (`200..65535`), overflow, campo vazio e lixo numérico antes de publicar sessão. Valor SDP vazio não acessa mais caractere inexistente. `replace()` não faz reserva com underflow unsigned quando padrão ausente é maior que entrada. Clipboard/nonce deixam de registrar conteúdo; tamanho só aparece após autorização. Fronteiras: 432 casos RTSP, 24 privacidade, 3 trim e 7 substituição passaram em fixtures CPU. Não houve ataque nem tráfego malformado contra serviço real.

### Hint de baixa latência WebRTC

Guarda anterior ignorava `jitterBufferTarget` quando API existia com valor inicial `null`, estado válido no contrato W3C. Detectada presença da propriedade sem exigir número prévio; proteção contra setter que lança preservada. Três pontos corrigidos, 14 testes CPU passam. Alvo continua sendo solicitação ao navegador, sem garantia de latência final. Caminho WebRTC é separado do cliente Android nativo.

### Diagnóstico reproduzível

- `tools/latency/analyze_trace.py`: percentis e estabilidade de intervalos, IDs repetidos/ausentes e tempos por estágio no mesmo clock. Não fabrica latência Windows→Android.
- `tools/latency/test_analyze_trace.py`: seis testes stdlib cobrindo cadência, stall, duplicação, clock domains e entradas inválidas.
- `tools/latency/Collect-HostDiagnostics.ps1`: inventário Windows/GPU sem alterar configuração.
- `tools/latency/Collect-AndroidDiagnostics.ps1`: snapshots ADB somente leitura no aparelho já autorizado; não instala APK nem presume MediaCodecList via comando shell fictício.
- `.github/workflows/latency-checks.yml`: checks focados CPU e sintaxe. Execução confirmada no GitHub Actions; resultado e limites abaixo.

## Validação local desta rodada

| Check | Resultado | Limite |
| --- | --- | --- |
| Regressão VBV nova | PASS; baseline falhou no assert esperado | Driver simulado, não ABI/firmware |
| NVENC API/eventos existentes | 18/18 PASS | CPU; compilação focada |
| Políticas WGC existentes | 29/29 PASS | CPU; sem captura real |
| Transporte/pacing existentes | 87/87 PASS; replay integrado 21/21 PASS | Helpers reais e snippets, sem sender/socket ao vivo |
| Mutex abandonado novo | PASS; baseline e três reintroduções falharam | Fluxo CPU, não recuperação física de GPU |
| Substituição de payload nova | 7 casos PASS; versão antiga falhou | Helper real extraído, sem stream ao vivo |
| Fronteiras RTSP/privacidade | 432 RTSP, 24 privacidade e 3 trim PASS | Snippets reais; sem request contra serviço |
| RFI/IDR existente | 36 cenários PASS | Controle de fluxo extraído, sem rede/GPU |
| Demanda de captura existente | 26 cenários e 175 comparações PASS | Fixture reparada; sem display real |
| Analisador de trace | 6 testes PASS; CLI A/B e recusa de sobrescrita PASS | Dados sintéticos, não ganho de streaming |
| Coletores PowerShell | Sintaxe PASS em PowerShell 5.1 | Não equivale à execução |
| Build completo x64 / testes no telefone | Não executados | Toolchain/dependências completas e ADB/aparelho não preparados |
| Coletor host em execução | Bloqueado por política de scripts local | Política preservada; inventário mínimo obtido por comandos diretos |
| Regressão WebRTC nova | 14 testes PASS | Funções reais extraídas, sem setter nativo/browser |
| Testes frontend já existentes | 101 PASS, 1 FAIL | Falta pacote `typescript`; suíte não integralmente verde |
| Auditoria de dependências npm | 22 entradas afetadas: 19 high, 2 moderate, 1 low | Advisory não comprova exploração; nenhum `audit fix` automático |

Fixture `test_video_capture_demand.py` não compilava no Windows porque replay omitia identidade/observador de recovery referenciado pelo callback atual. Acrescentados campo e stub de observação no teste, não no produto. Ele valida demanda/pacing, não lookup de recovery. Não transformar fixture sintética em evidência de FPS real.

Ferramentas de trabalho portáteis ficaram em `%LOCALAPPDATA%\Temp\opencode`: Git, Python 3.13.12, Zig 0.14.1 (SHA256 validado contra índice oficial) e header `nlohmann/json` 3.11.3 usado no check de captura. Nenhuma nova dependência do produto. Clone recursivo geral interrompido durante dependências; submódulos raiz foram alinhados aos pins originais e dependências exigidas pelos checks inicializadas. Dependências recursivas ainda incompletas não fazem parte dos commits.

Comandos reproduzíveis dos checks específicos estão nos documentos de área. Ambiente temporário usado pela sessão principal:

```powershell
$env:TEMP = "$env:LOCALAPPDATA\Temp\opencode"
$env:TMP = $env:TEMP
$env:CPLUS_INCLUDE_PATH = "$env:LOCALAPPDATA\Temp\opencode\include"
& "$env:LOCALAPPDATA\Temp\opencode\python\python.exe" tools/latency/test_analyze_trace.py
& "$env:LOCALAPPDATA\Temp\opencode\python\python.exe" tests/unit/test_nvenc_bitrate.py --compiler "$env:LOCALAPPDATA\Temp\opencode\cxx.cmd"
& "$env:LOCALAPPDATA\Temp\opencode\python\python.exe" tests/unit/test_video_rfi_retry.py --compiler "$env:LOCALAPPDATA\Temp\opencode\cxx.cmd"
& "$env:LOCALAPPDATA\Temp\opencode\python\python.exe" tests/unit/test_video_capture_demand.py . "$env:LOCALAPPDATA\Temp\opencode\cxx.cmd"
```

Python embarcado desativa inclusão automática do diretório do script no `sys.path`; ambiente temporário recebeu `tools/latency` no arquivo `python313._pth` para teste local. Python padrão/CI não precisa disso. Caminhos temporários não são configuração obrigatória do projeto.

## GitHub Actions confirmado

Commit verificado: `94d6bd633cbf4dafb0f23821d89f10594a44dfea`, em 2026-11-14.

- [Latency diagnostics checks — PASS](https://github.com/flugeru/Vibepollo/actions/runs/43627911720): regressões CPU de NVENC, mutex abandonado e transporte, hints WebRTC, analisador e parse dos coletores PowerShell passaram no runner Ubuntu. Nenhum comando de coleta em dispositivo foi executado.
- [CI herdado — success, cobertura limitada](https://github.com/flugeru/Vibepollo/actions/runs/43627911835): somente job `Release candidate` executou. Jobs Windows, Arch Linux, validação de artefato Linux e assinatura foram ignorados (`skipped`). Esse verde **não comprova build Windows x64**.
- [Deploy GitHub Pages — FAIL](https://github.com/flugeru/Vibepollo/actions/runs/43627911864): montagem estática passou; `Configure GitHub Pages` retornou `HttpError: Not Found` ao consultar o site Pages. Anotação pede habilitar/configurar Pages no repositório. Deploy foi ignorado. Configuração de publicação do fork não foi alterada; falha não é evidência de erro no binário de streaming.

## Próxima validação no hardware

1. Preparar build x64 com toolchain/dependências do projeto e executar testes CTest; confirmar binário efetivamente em execução. Preservar instalação/configuração atual até build validado. Não usar instalador de fork como release assinada do upstream.
2. Conectar POCO por USB com autorização manual e coletar versão do APK/firmware, decoder efetivo, display e thermal. Revisar dumps antes de compartilhar: podem conter dados pessoais. Política de scripts local não será contornada automaticamente.
3. Obter capacidades MediaCodecList em build instrumentada/probe autorizado; testar bitstreams reais em Surface. Só então decidir envelope AV1/HEVC, 8/10-bit, resolução e FPS.
4. Registrar baseline por frame e input-to-photon; alternar A/B com mesma qualidade/cena/rede. Instrumentação/exportador CSV ainda precisa ser conectado ao cliente/host: analisador não faz captura automática.
5. Avaliar presets/multipass/AQ e pacing isoladamente. Promover somente melhora repetível de caudas/drops sem regressão visual, térmica, áudio ou recovery. Backlog técnico e desconhecidos permanecem nos relatórios; nenhuma promessa numérica substitui essa medição.
