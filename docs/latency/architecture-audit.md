# Auditoria de arquitetura, transporte e dependências

Data: 2026-11-14. Baseline informado: `5e6ed8594184212d429bb093d7fd47904c9a6fa6`.

## Escopo e limites

Leitura focada de `src/stream.cpp`, `src/rtsp.cpp`, `src/stream_protocol.*`, `src/stream_broadcast_runtime.*`, `src/video_adaptive_pacing.h`, `src/thread_safe.h`, `src/sync.h`, caminhos relevantes de `src/webrtc_stream.cpp` e `src/confighttp.cpp`, configurações/frontend WebRTC, CMake, downloads de dependências e CI Windows. Não constitui auditoria integral linha por linha, pentest, certificação criptográfica nem medição de latência física.

NVENC, captura e decoder Android ficam com auditorias separadas. Escopo ampliado pelo agente coordenador para corrigir T1 e S1: validação de tamanho na fronteira RTSP e remoção de texto de clipboard/nonce dos logs, além da reserva de `replace()`. Defaults de qualidade, bitrate, FPS, rede e política de override preservados. Arquivos próprios: `src/stream.cpp`, `src/rtsp.cpp`, `tests/unit/test_stream_replace.py`, `tests/unit/test_transport_boundaries.py`, registro isolado em `tests/CMakeLists.txt` e este relatório. Sem commit ou staging por este agente; commits remotos são geridos pela sessão principal. Submodules não foram tratados como alterações próprias.

Build completo não executado: CMake/CTest não encontrados no PATH e dependências nativas ausentes. Durante leitura inicial submodules estavam parciais; sessão principal depois confirmou todos os submodules raiz nos pins originais, incluindo nanors `19f07b5`, libdisplaydevice e libvirtualdisplay; dependências recursivas de build ainda incompletas. Testes CPU abaixo não substituem build/link da aplicação, driver, tráfego real, navegador ou execução do CI. Referências de linha correspondem ao checkout inspecionado; trabalhos paralelos podem deslocá-las.

## Resultado e prioridade

| ID | Prioridade | Estado | Evidência/efeito |
| --- | --- | --- | --- |
| T1 | Alta | Corrigido na fronteira RTSP; teste CPU de 432 casos | Tamanho fora de `200..65535`, overflow e texto inválido agora recebem 400 antes de sessão ativa; independente do teto de pacing. |
| T2 | Média | Corrigido; reproduzido CPU antes/depois | `replace()` fazia underflow unsigned no `reserve()` quando padrão ausente superava entrada. |
| W1 | Média | Corrigido em patch separado; 14 testes CPU | `jitterBufferTarget` inicialmente `null` impede aplicação do alvo de baixa latência. |
| S1 | Média, privacidade | Corrigido; 24 casos CPU | Clipboard e nonce registram somente tamanho após autorização, nunca payload. |
| O1 | Baixa | Discrepância de estado por leitura | API anuncia bitrate configurado como bitrate do encoder mesmo após ABR alterar alvo. |
| O2 | Baixa | Contaminação de métrica por desenho | Timestamp anterior global mistura sessões no intervalo entre capturas. |
| B1 | Baixa | Configuração contraditória; consumo final não confirmado | Opção KWin GPU bridge desligada para SteamOS volta a ON em dependências comuns. |
| D1 | Triagem pendente | Inventário npm executado | 22 entradas de pacotes afetados: 19 high, 2 moderate, 1 low; não são 22 vulnerabilidades únicas nem exploração comprovada. |

Prioridade indica triagem técnica, não CVSS calculado. Hipóteses sem reprodução ficam separadas abaixo.

## T1 — tamanho de pacote RTSP não validado incondicionalmente

- `src/rtsp.cpp:2370` aceita `x-nv-video[0].packetSize` do cliente. `2373–2387` verifica intervalo somente do override global, e somente quando esse override reduz pedido; não valida intervalo do pedido em todos os caminhos.
- `src/stream.cpp:855–859,881–883` tem guardas de tamanho na admissão com teto de pacing positivo. `src/config.cpp:1012` mantém teto zero por padrão; esse caminho não fornece validação universal.
- `src/stream.cpp:2493` calcula `% (session->config.packetsize - sizeof(NV_VIDEO_PACKET))`. Estrutura packed tem 16 bytes em `third-party/moonlight-common-c/src/Video.h:27–35`. Pedido 16 deixa divisor zero; valores menores permitem subtração unsigned inválida para finalidade de payload e cálculos subsequentes.
- Cálculo fica antes do `try` local em `stream.cpp:2657`; `catch (std::exception)` posterior não protege divisão por zero nem esse trecho.

Pré-condição: alcançar negociação de sessão RTSP aceita/pending; não demonstrado acesso irrestrito por cliente não autorizado. Há associação e verificação de sessão em `rtsp.cpp:425–465,824–847`. Não foi enviado ANNOUNCE malformado contra serviço real; impacto de encerramento do processo é inferência do caminho e da operação inválida, não crash de servidor observado.

Correção aplicada em `src/rtsp.cpp`: `std::from_chars` para inteiro, consumo integral do campo, rejeição de overflow e intervalo `config::PACKETSIZE_MIN..PACKETSIZE_MAX` antes de aplicar override/publicar sessão. Constantes existentes em `src/config.h:28–32`: **200..65535**, não 1024. Pedidos válidos mantêm comportamento: override válido só reduz tamanho; zero desliga override; override inválido continua ignorado com warning quando aplicável. Pedido inválido não é legitimado por override. Teto de pacing não participa dessa validação. Limite 65535 existente foi preservado; isso não certifica adequação de MTU/fragmentação para qualquer rede.

Guarda de valor SDP vazio também corrigida no mesmo caminho (`!val.empty()` antes de `val.back()`): sem ela, campo vazio podia falhar antes da nova validação. Nenhum parser numérico dos demais campos foi alterado.

`tests/unit/test_transport_boundaries.py` compila trecho literal de admissão e override em fixture CPU: 432 combinações, tetos 0/200000, override desligado/válido/inválido, negativos/15/16/17, mínimo e máximo com adjacentes, valores normais 1024/1376/1408, overflow int/decimal longo, campo ausente/vazio, lixo e zero à esquerda. Três casos exercitam trim SDP real. Variante em memória sem validação falhou no assert de resposta 400. Não é handler completo de ANNOUNCE nem teste de socket; source check confirma trecho antes de `stream::session::alloc()`. Headers de pacing e comportamento dos defaults não foram modificados.

## T2 — reserva em `stream::replace()`

`src/stream.cpp:1357–1372` substitui primeira ocorrência em payload. Expressão anterior:

```cpp
replaced.reserve(original.size() + _new.size() - old.size());
```

Antes de procurar padrão, soma/subtração de `size_t` podia produzir tamanho enorme. Exemplo: entrada `"x"`, padrão `"header absent"`, substituição `"y"`. Nenhuma substituição deveria ocorrer, mas `reserve()` lançava `std::length_error`.

Correção:

```cpp
replaced.reserve(original.size());
```

Bytes e semântica de primeira ocorrência preservados, incluindo padrão vazio. Vetor pode realocar quando substituição aumenta conteúdo; nenhum novo limite ou política criado.

Callsite `stream.cpp:2478–2485` aplica helper a replacements de frames IDR. **Esse callsite também precede `try` de `2657`**: não é correto atribuir essa exceção ao `catch` de `3261–3263` nem afirmar apenas atraso de 100 ms. Exceção não tratada nesse trecho pode encerrar thread/processo conforme fronteira externa; reprodução isolada encerrou executável. Frequência em tráfego normal não medida.

`tests/unit/test_stream_replace.py` extrai corpo literal de produção, compila C++23 standalone e executa sete asserts: padrão ausente maior, entrada vazia, substituição vazia sem match, primeira ocorrência, crescimento, remoção e padrão vazio. Versão corrigida: PASS. Expressão antiga injetada somente em memória no leitor do teste: falha esperada `std::length_error: vector`, exit `3221226505`. Fonte em disco não foi revertida durante comparação. Teste cobre helper, não tradução completa de `stream.cpp`.

## W1 — configuração de jitter buffer ignora estado inicial válido

No baseline, `src_assets/common/assets/web/services/webrtc.ts:152–171`, especialmente `166`, exigia `typeof receiver.jitterBufferTarget === 'number'` antes de atribuir alvo. Especificação define propriedade nullable, valor inicial `null`, unidade milissegundos. Presença da API não exige valor numérico inicial. Refresh em `784–796` repetia mesma função, mantendo problema. Legacy continha mesma guarda em `src_assets/common/assets/web-legacy/utils/webrtc/client.ts:327,377`.

Reprodução CPU extraiu `applyReceiverLatencyHints()` real, removeu tipos com `node:module.stripTypeScriptTypes`, executou com `{ jitterBufferTarget: null }` e alvo `5`: resultado continuou `null`. Confirma ramo incorreto, não comportamento de setter/browser real nem ganho de latência. Patch separado usa `'jitterBufferTarget' in receiver` em três guards, preservando valores e tratamento de erro. Novo teste cobre null, número, API ausente e setter que lança: 14 PASS. Ver [webrtc-hints.md](webrtc-hints.md); ganho real depende do navegador.

Contrato consultado via Context7 (`/websites/w3_tr_webrtc`) e fonte normativa: https://www.w3.org/TR/webrtc/#dom-rtcrtpreceiver-jitterbuffertarget . Alvo é solicitação ao navegador, não garantia de atraso final de 5 ms.

## Segurança e robustez

### S1 — clipboard nos logs antes de autorização

Antes do patch, `src/stream.cpp:1927–1933` incluía conteúdo recebido de clipboard em INFO antes de testar permissão; ramo de nonce em `1936–1942` fazia o mesmo. Dado recebido podia persistir mesmo quando sincronização estava proibida. Não foi provada presença de senha nem acesso público ao log.

Correção aplicada aos dois handlers: checagem de permissão existente permanece primeiro; INFO registra somente tipo do pacote e `payload.size()` depois da autorização. Payload nunca entra no logger. Caminho negado mantém diagnóstico de permissão sem tamanho nem conteúdo. Nenhuma autorização foi relaxada.

Teste CPU compila corpos literais dos dois handlers com logger capturado: 24 casos, permissões ausente/correta/incorreta/ambas, payload vazio/texto privado/multilinha. Compara log completo e ausência do conteúdo; permitido tem somente tamanho, negado não tem tamanho. Variante em memória que restaura texto no log falhou. Não cobre logger real, criptografia ou despacho completo de controle.

### Guardas positivas observadas

- `stream.cpp:1164–1168` rejeita controle plaintext quando protocolo exige versão 13 criptografada.
- `stream.cpp:1948–2013` verifica envelope/tamanho/tag do controle criptografado.
- `stream.cpp:1893–1905` verifica permissão de comando e limite de índice antes de executar ação.
- RTSP associa candidatos pending e autenticação de sessão (`rtsp.cpp:425–465,824–847`); buffer limitado a 2048 bytes (`743`). Isso não prova ausência de falhas nos parsers.
- Falha temporária de envio tem tratamento distinto de exceção genérica (`stream.cpp:3244–3263`); contabiliza prefixo de pacotes aceito sem declarar frame entregue (`2648–2655`). Sono genérico de 100 ms existe, mas somente para exceções dentro daquele `try`.

### Hipóteses abertas; nenhuma correção automática

| Risco | Base de leitura | Limite e próximo ensaio |
| --- | --- | --- |
| FEC excessivo/controlado pelo cliente | `rtsp.cpp` recebe mínimo sem limite evidente; `stream.cpp:2545–2548,1278–1294,1331–1333` calcula paridade/aloca/chama RS. | Nanors agora disponível: `rs.h:6` limita total a 255; `rs.c:82–117` retorna NULL para quantidade inválida; `164–168` dereferencia `rs`. Caller não verifica NULL. Contrato confirma risco condicional, mas negociação/capacidade efetiva não reproduzidas. Próximo patch exige teste integrado de mínimo FEC e alocação; não alegar crash ao vivo. |
| Parser de atributo vazio | Antes do patch `rtsp.cpp:2330` indexava `val[val.size() - 1]`. | Corrigido junto de T1 para alcançar rejeição segura do campo vazio. Três casos CPU do trim real; sem request completo de rede. |
| Conversão numérica sem validação/overflow robustos | `src/utility.h:530–547`, usado na negociação. | Avaliar cada campo e conversões signed/unsigned; não generalizar resultado para campos com validação própria, como FPS. |
| Conexão pending lenta | Sem timeout de leitura de socket evidente no trecho `rtsp.cpp:256–753`; timer de pending não equivale necessariamente a deadline de cada leitura. | Testar expiração real e liberação de recursos; não declarar slowloris irrestrito. |
| Referência em comando detached | `stream.cpp:1908` captura configuração por referência em trabalho detached. | Provar se reload/teardown pode invalidar objeto antes de alegar dangling reference. |
| Self-assignment/exceção em sincronização | `src/sync.h:29–48` usa lock manual de dois mutexes e operações `noexcept`. | Self-assignment pode tentar lock duplicado; tipo que lança pode terminar processo. Sem callsite/reprodução no caminho auditado; baixa prioridade até provar alcance. |

## Filas, pacing e frame timing

### Caminho RTSP/UDP

- Sender vídeo único (`src/stream.cpp:2358–2360`) consome fila compartilhada; sender áudio separado (`3270–3272`). Sono de pacing no processamento de frame (`2992–3008`) pode reter atendimento de outra sessão. Dívida/timestamps de pacing compartilhados (`2405,2854–2855,3055–3056`) refletem desenho agregado. Hipótese de head-of-line blocking, não regressão medida. Ensaio necessário: duas sessões, uma limitada, métricas por sessão e p95/p99 de idade na saída.
- `src/thread_safe.h:365–368` define capacidade default 32; overflow em `444–450` limpa fila inteira, não somente item mais antigo. `814–826` reutiliza fila por nome e primeiro produtor define capacidade. Não assumir capacidade efetiva 32 sem conferir ordem/callsite de criação. Sender observado não fornece histogramas de idade/overflow suficientes para quantificar custo.
- Política de limpar backlog evita acúmulo ilimitado, mas pode interromper cadeia inter-frame se usada indiscriminadamente com frames codificados. Não substituir por latest-only sem contrato de IDR/referências. Áudio precisa análise própria de continuidade, não política copiada de vídeo.
- Controlador em `src/video_adaptive_pacing.h` controla envio/pacing, não deve ser descrito como ABR do encoder. Teto explícito, admissão e separação entre bitrate codificado e bytes de fio/FEC aparecem nos testes CPU; sucesso de asserts não mede precisão do relógio/UDP real.

### O2 — métrica de intervalo cruza sessões

`stream.cpp:2406,2458–2463` mantém `last_frame_timestamp` no broadcast global, não por sessão. Frames intercalados podem gerar intervalos misturados ou negativos e contaminar diagnóstico. Não altera timestamp RTP por esse fato. Corrigir observabilidade separadamente; teste com duas sessões e captura fora de ordem.

### Caminho WebRTC e ABR

- Filas raw/encoded limitadas a quatro (`src/webrtc_stream.cpp:130–131,1933–1936`); capacidade não é atraso fixo de quatro frames.
- Encoder compartilhado escolhe menor alvo entre peers (`2031–2050`), usa histerese de 5% e passos mínimos (`154–156,2065–2075`). Publicação de ajuste é suprimida com RTSP ativo (`2054`). Teardown recalcula alvo de peers remanescentes (`5937–5939`). Descrever isso como política compartilhada, não controle independente por cliente.
- Sender observa idade (`5125–5130`); modos balanced/smoothness preservam cadeia (`5161–5167`), enquanto descarte para baixa latência depende de IDR. Reduzir fila sem esse requisito pode agravar recuperação.
- Ajuste de timestamp para alvo de envio (`5287–5290`) significa que algumas métricas downstream não preservam tempo original de captura. Medição ponta a ponta precisa identidade de frame e timestamps distintos para captura, encode, fila, envio e apresentação.
- Espera de áudio em fatias de 2 ms (`5069–5078`) decrementa duração solicitada, não tempo efetivo decorrido. Oversleep acumulado é hipótese, não valor medido; testar com relógio real sob carga antes de mudar espera.

### O1 — bitrate exposto não acompanha alvo adaptativo

`src/confighttp.cpp:743–746` serializa requested/encoder bitrate a partir de `state.bitrate_kbps`. Valor inicial é preenchido em `webrtc_stream.cpp:5819`; controlador muda target em `2123–2134` e publica evento em `2053–2088` sem atualizar esse estado no trecho observado. Diagnóstico pode mostrar valor configurado, não alvo aplicado. Além disso, envio do evento não confirma reconfiguração do driver: solução deve distinguir requested, target e applied, sem inventar confirmação. Não corrigido.

## Build, CI e cadeia de dependências

- C++23 exigido (`CMakeLists.txt:11–12`). `BUILD_TESTS` default ON e WebRTC default OFF (`cmake/prep/options.cmake:11,18`). Logo build local padrão não cobre WebRTC automaticamente.
- PR chama Windows build com WebRTC/testes (`.github/workflows/pr-build.yml:82–90`); workflow Windows passa flags e executa CTest (`ci-windows.yml:711,720,755–760`). Ações do workflow PR inspecionado usam hashes fixados. Arquivo de CI demonstra intenção, não execução bem-sucedida nesta máquina.
- **B1:** `CMakeLists.txt:68–73` força `BUILD_VIBESHINE_KWIN_GPU_BRIDGE OFF` para SteamOS; `cmake/dependencies/common.cmake:24–25` volta a forçar ON antes do subdiretório. Contradição real de configuração. Consumo final por libvirtualdisplay não confirmado nesta leitura inicial; checkout raiz foi completado depois pela sessão principal. Não declarar falha de build SteamOS reproduzida.
- FFmpeg: download com SHA256/TLS e validação de cache (`cmake/dependencies/ffmpeg.cmake:25–38,45–65`). Verificação do arquivo não autentica, por si só, origem do hash; origem/revisão do manifesto continuam parte da confiança.
- WebRTC downloader: SHA256 e identificação da saída (`scripts/download_webrtc_release.ps1:65–69,115–131`). `145–149` remove `OutDir` antes da cópia: instalação não atômica pode perder cache válido em falha de I/O. Hipótese operacional por leitura; script não executado. Próximo patch deve preparar diretório temporário e trocar somente após validação.

### D1 — inventário npm, não pentest

Executado em `src_assets/common/assets/web`:

```powershell
npm.cmd audit --package-lock-only --ignore-scripts --json
```

Retorno 1 por advisories: 22 entradas de pacotes, 19 high, 2 moderate, 1 low, zero critical. Resultados dependem da base npm na data da consulta. Sem instalação, `audit fix`, modificação intencional de lockfile ou exploração. Entradas transitivas podem propagar mesmo advisory e não devem ser somadas como falhas únicas.

- Manifesto usa Vue 3.5.18, vue-i18n 11.1.11 e Vite 4.5.14; lock fixa Playwright 1.55.0 (`package-lock.json:1625–1632`). Classificação runtime/build/test depende de uso, não somente rótulo npm.
- Vue I18n: audit aponta https://github.com/advisories/GHSA-83rm-6c8r-g728 (`escapeParameter`/`srcdoc`). Necessário provar entrada não confiável em traduções/renderização antes de afirmar XSS explorável neste produto.
- Vue/server-renderer: dependência presente não prova SSR em execução; não foi confirmado servidor SSR neste frontend.
- Playwright: https://github.com/advisories/GHSA-7mvr-c777-76hp afeta `<1.55.1`; advisory oficial descreve instalação de browsers no macOS com verificação TLS desabilitada em scripts. Pacote de teste/dev; não prova comprometimento de CI Windows nem tráfego de streaming inseguro.
- Vite 4.5.14 aparece afetado por advisories posteriores, incluindo https://github.com/advisories/GHSA-93m4-6634-74q7 e outros listados pelo audit. **Não atribuir GHSA-859w-5945-r5v3 a 4.5.14: já corrigido nessa versão.** Servidor dev em loopback (`vite.config.ts:26`) reduz superfície, não substitui atualização/triagem de rotas locais.
- Proxy HTTPS usa `secure: false` em `vite.config.ts:29–37`, somente configuração de desenvolvimento inspecionada. Não significa TLS de produção desligado.
- Rollup, PostCSS, esbuild, serve e dependências de glob também aparecem no inventário. Priorizar atualização compatível e testar build; sugestão npm de Vite major não é autorização para migração automática. Triagem deve separar conteúdo de build não confiável, servidor dev exposto, instalação de ferramentas e bundle servido ao usuário.

## Checks CPU executados

Ferramentas fornecidas: Zig 0.14.1 via wrapper `C:\Users\Flugel\AppData\Local\Temp\opencode\cxx.cmd`, Python em `C:\Users\Flugel\AppData\Local\Temp\opencode\python\python.exe`, Git portátil em `C:\Users\Flugel\AppData\Local\Temp\opencode\mingit\cmd\git.exe`. Validação de checksum do compilador foi fornecida pelo agente coordenador/sessão principal; não é resultado desta auditoria.

| Check | Resultado | Cobertura real |
| --- | --- | --- |
| `test_stream_replace.py` com função corrigida | PASS, 7 casos | Corpo extraído de helper real; sem TU completo; `-UNDEBUG` mantém asserts ativos. |
| `test_transport_boundaries.py` | PASS, 432 RTSP + 24 privacidade + 3 trim SDP | Trechos literais em fixtures CPU; sem TUs completos nem sockets. |
| Variantes temporárias sem validação RTSP/com payload no log | Duas falhas esperadas, exit `3221226505` | Asserts detectam ausência de 400 e conteúdo indevido; fonte em disco preservada. |
| Mesmo teste com expressão antiga injetada em memória | Falha esperada, `std::length_error`, exit `3221226505` | Reproduz underflow/reserva inválida no baseline. |
| `test_video_pacing.cpp`, `test_video_adaptive_pacing.cpp`, `test_stream.cpp` + `src/stream_protocol.cpp` | 87/87 PASS | Helpers reais de protocolo/pacing e testes de stream leves; não sender/socket real. |
| Replay `test_video_pacing_integration.py` | 21/21 PASS | Snippets extraídos e fixtures; controles de broadcast reais linkados de `src/stream_broadcast_runtime.cpp`. Não compila RTSP/NVHTTP ou `stream.cpp` completo. |
| `node --experimental-strip-types --test scripts/*.test.ts` no frontend | 101 PASS, 1 FAIL | Falha `ERR_MODULE_NOT_FOUND` para pacote `typescript`; suíte não está integralmente verde. Nada instalado para ocultar falha. |
| Função real `applyReceiverLatencyHints()` extraída, receiver com valor null | Bug reproduzido | Alvo 5 ms ignorado; sem navegador/setter nativo. |
| `npm audit --package-lock-only --ignore-scripts --json` | Exit 1, 22 entradas | Inventário lockfile/advisories, não exploração. |

Comandos reproduzíveis de transporte, em PowerShell na raiz:

```powershell
$cxx = 'C:\Users\Flugel\AppData\Local\Temp\opencode\cxx.cmd'
$python = 'C:\Users\Flugel\AppData\Local\Temp\opencode\python\python.exe'
$out = 'C:\Users\Flugel\AppData\Local\Temp\opencode'
& $python tests/unit/test_stream_replace.py $cxx
& $python tests/unit/test_transport_boundaries.py $cxx
& $cxx -std=c++23 -O0 -g0 -pthread -I. -Ithird-party/googletest/googletest/include -Ithird-party/googletest/googletest tests/unit/test_video_pacing.cpp tests/unit/test_video_adaptive_pacing.cpp tests/unit/test_stream.cpp src/stream_protocol.cpp third-party/googletest/googletest/src/gtest-all.cc third-party/googletest/googletest/src/gtest_main.cc -o "$out\architecture-transport-tests.exe"
& "$out\architecture-transport-tests.exe" "--gtest_output=json:$out\architecture-transport-tests.json"
& $python tests/unit/test_video_pacing_integration.py . $cxx --generate-only --output-dir "$out\architecture-pacing-replay"
& $cxx -std=c++23 -O0 -g0 -pthread -I. "-I$out\include" "-I$out\architecture-pacing-replay" -Ithird-party/googletest/googletest/include -Ithird-party/googletest/googletest "$out\architecture-pacing-replay\video_pacing_integration.cpp" "$out\architecture-pacing-replay\stream_protocol.cpp" src/stream_broadcast_runtime.cpp third-party/googletest/googletest/src/gtest-all.cc third-party/googletest/googletest/src/gtest_main.cc -o "$out\architecture-pacing-replay.exe"
& "$out\architecture-pacing-replay.exe" "--gtest_output=json:$out\architecture-pacing-replay-results.json"
```

Primeira compilação manual do replay falhou por `nlohmann/json.hpp` ausente; include cache fornecido pela sessão principal em `$out\include` (v3.11.3) resolveu. Binários, snippets e JSON de resultados permanecem fora do repo nos caminhos acima; são artefatos locais, não dependências vendorizadas novas.

Registro CMake em bloco próprio, longe do bloco concorrente NVENC: `test_component_stream_replace` e `test_component_transport_boundaries`, com Python, componentes habilitados e compilador GNU/Clang, label `component`, timeout 120 s. `cmake`/`ctest` ausentes do PATH; registro não foi configurado/executado via CTest. Execuções diretas acima passaram.

Resultados externos, fornecidos pela sessão principal e **não executados por este agente**: captura `test_video_capture_demand.py` PASS 26 cenários/175 comparações após ajuste de fixture Windows callback recovery; RFI PASS 36 cenários. Testes do analisador permanecem sob sessão principal; nenhum resultado foi inferido.

## Próximos passos, sem alterar política automaticamente

1. Validar T1/S1 em build completo e integração RTSP/controle; abrir patch separado para contrato FEC de 255 shards/retorno NULL, com limites negociados e falha de alocação.
2. Validar hint de jitter buffer corrigido em navegador real; alvo é solicitação, não atraso garantido. Teste CPU está em [webrtc-hints.md](webrtc-hints.md).
3. Instrumentar idade/overflow por sessão e requested/target/applied bitrate antes de alterar filas, pacing ou ABR.
4. Completar build nativo/WebRTC e executar CTest/CI; depois ensaio com duas sessões e rede controlada. Comparar latência p50/p95/p99, perda/recuperação e qualidade, sem alegar ganho pelos testes CPU.
5. Triar advisories por alcance e atualizar dependências com build/regressão explícitos; manter HTTPS e verificação de artefatos.
