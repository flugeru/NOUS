# Auditoria NVENC, captura Windows e pacing

Data: 2026-11-14. Baseline informado: `5e6ed8594184212d429bb093d7fd47904c9a6fa6`.
Escopo: leitura de `src/nvenc/*`, caminhos NVENC/captura em `src/video.cpp`, `src/video.h`, `src/video_policy.h`, `src/thread_safe.h`, captura DXGI/WGC e conversão em `src/platform/windows`, configurações e testes relacionados. Não é certificação de todos os backends ou do sistema inteiro.

## Resultado

Correção inicial: retirar piso arbitrário de 100.000 bits na reconfiguração do VBV. Após ampliação explícita da propriedade, corrigidos também três ramos que usavam superfície/mutex abandonados na captura/conversão Windows. Nenhum default de preset, multipass, AQ, resolução, split-frame, filas ou threads alterado. Testes CPU executam métodos/ramos de produção contra limites falsos. Nenhuma carga GPU ou alteração de driver/sistema. Commits remotos organizados pela sessão principal.

RTX 5060, driver 621.24 e 8151 MiB foram informados pela sessão principal após `nvidia-smi`; não medidos por esta auditoria. Fonte oficial informa **um NVENC** na RTX 5060. Não recomendar split-frame multi-NVENC para esse modelo. Disponibilidade nominal de AV1 não prova throughput/latência de qualquer perfil.

## Achado corrigido — VBV cresce durante ABR e permanece inflado

**Comprovado por execução CPU; impacto no buffer configurado, não em latência física medida.**

- `src/nvenc/nvenc_base.cpp:497–501`: criação usa `bitrate / framerate` bits, mais aumento explícito configurado.
- `src/nvenc/nvenc_base.cpp:1154–1159`: reconfiguração antiga escalava proporcionalmente, mas aplicava `max(scaled, 100000)`.
- `src/nvenc/nvenc_base.cpp:1177–1179`: valor inflado era salvo; aumentos seguintes escalavam a partir dele.
- `src/video.cpp:5506–5523`: pedidos ABR podem chamar esse método ao vivo, sem reconstruir sessão.

Exemplo determinístico: 12 Mbps / 240 fps configura 50.000 bits, janela nominal 4,167 ms. Redução a 2 Mbps deveria configurar 8.333 bits; código antigo enviava 100.000 bits, janela nominal 50 ms. Retorno a 12 Mbps enviava 600.000 bits, mantendo 50 ms. Até pedido de bitrate igual inflava buffer abaixo de 100 kbit. Isso não significa que driver sempre retém imagem por 50 ms: VBV é restrição de rate control/HRD, não fila CPU nem medição do tempo de codificação.

Correção mantém escala existente com piso de **1 bit**, pois zero pede default do driver. Guardas de bitrate, cálculo em `uint64_t`, VBV não especificado, rollback após rejeição e política reset/IDR existente ficam intactos. Aumento explícito `nvenc_vbv_increase` continua proporcional. Truncamento inteiro permanece: ida/volta do exemplo resulta 49.998 bits; não foi criado estado extra para eliminar erro de poucos bits. Limite: se driver rejeitar valor muito pequeno, método continua retornando falha para caminho de reconstrução já existente; teste não valida limites do firmware.

Teste: `tests/unit/test_nvenc_bitrate.py`. Extrai `nvenc_base::set_bitrate()` literalmente; simula estruturas mínimas e chamada de reconfiguração. Verifica redução, subida, pedido igual, VBV aumentado, arredondamento mínimo positivo, VBV zero, bitrate anterior zero, rejeição sem persistir estado e validação de entrada. `-UNDEBUG` mantém asserts ativos. Não prova ABI/driver; não substitui compilação da aplicação. Variante temporária com piso antigo falhou exatamente no assert de 8.333 bits; versão corrigida passou.

## Achado corrigido — superfície abandonada ainda usada

**Contrato oficial contradizia fluxo; regressão CPU comprovada. Ocorrência e impacto em hardware não medidos.**

- `src/platform/windows/display_wgc.cpp:379–388`: `WAIT_ABANDONED` agora retorna `capture_e::reinit` antes do guard de `ReleaseSync`, leitura IPC ou cópia GPU.
- `src/platform/windows/display_vram.cpp`, `convert()` (aquisição originalmente em 617): apenas `S_OK` permite prosseguir; abandono retorna `-1` antes do guard, leitura de metadados e shader sampling.
- `src/platform/windows/display_vram.cpp:283–293`, `texture_lock_helper::lock()`: terceiro ramo com mesmo defeito. Apenas `S_OK` marca `_locked`; abandono retorna false, e destrutor/move não liberam lock inexistente. Chamador `get_locked_d3d_img` retorna imagem nula e snapshot retorna erro antes da cópia.
- Microsoft documenta que `IDXGIKeyedMutex::AcquireSync` com `WAIT_ABANDONED` significa superfície e mutex inconsistentes; ambos precisam ser liberados e recriados. Semântica não equivale à recuperação de mutex Win32 comum.

### Lifecycle verificado por leitura

WGC não sobrescreve `commit_pending_capture_metadata()`; default `src/platform/common.h:863–865` retorna false. `display_base.cpp:630–633` propaga reinit sem publicar imagem inválida. Em captura assíncrona, `video.cpp:3476–3510` fecha admissão/notifica encoders e descarta pool; `3515–3557` aguarda referências antigas e libera superfícies; `3563–3584,3619` não retém display WGC e recria display/backend. `_frame_locked` ainda não foi marcado nesta falha; `display_wgc.cpp`, `release_snapshot()`/destrutor só chamam IPC release quando esse flag está ativo. Novo pool usa novos objetos, não textura inconsistente.

Em captura síncrona, `video.cpp:6328–6347` destrói sessões, default de commit não retém recursos e retorna reinit; `captureThreadSync():6386–6393` invoca novo `encode_run_sync`, cujo display é local. Falha de conversão `-1` segue caminho existente (`video.cpp:5641–5644` no async; ramo equivalente sync sinaliza shutdown): nenhuma codificação recebe aquela superfície. Pode reconstruir encoder ou encerrar sessão conforme backend; não prometida recuperação transparente do cliente nem reinício global imediato de captura quando falha veio somente do encoder.

Teste `tests/unit/test_keyed_mutex_abandoned.py`: extrai literalmente aquisição/guards de WGC e encoder, mais `lock()` e destrutor do helper. Stubs contam aquisições/releases e cruzamentos do limite de leitura/cópia; `S_OK`, abandono, timeout e falha genérica cobertos. Baseline exato falhou; reintroduções isoladas de cada um dos três ramos antigos também falharam; patch passou com Zig e clang (`-Werror`). Teste não executa DXGI, COM, driver, teardown real ou recriação física. Lifecycle acima é análise estática, não teste end-to-end.

Timeout de 3.000 ms continua intacto. Outro tratamento de abandono em `src/platform/windows/ipc/ipc_session.cpp:771–779` já pede reinit; seu `ReleaseSync` defensivo não foi alterado nesta expansão, restrita aos dois arquivos autorizados. Não reduzir timeout nem expandir refactor sem prova.

## NVENC: observações e riscos não corrigidos

### Uma transação por frame; modo assíncrono da API não é pipeline de vários frames

- `nvenc_base.cpp:821–826`: um bitstream de saída.
- `nvenc_base.cpp:966–988,1038–1082`: map, submit, espera, lock, cópia CPU, unlock, unmap antes de retornar.
- `nvenc_d3d11.cpp:34`: evento auto-reset. `nvenc_base.cpp:1047`: deadline de 100 ms; timeout não é espera fixa em todo frame.
- `src/video.cpp:1343–1364`: rejeita saída cujo índice não corresponde ao submit; `3737–3764` só publica pacote não vazio.

Não há prova de fila profunda NVENC nesse caminho. Existe serialização que impede sobrepor próxima conversão/submissão com saída atual. Guia NVIDIA 13.1 §6.1 descreve vários buffers e thread de saída; §3.9 admite mínimo de `1 + B` e §6.3 favorece mais buffers para throughput. Não converter isso em patch automático: profundidade extra pode aumentar idade de frames e exige identidade/timestamps, cancelamento e teardown corretos.

Guia 13.1 §6.3 alerta especificamente para `AcquireNextFrame` e lock/unlock de bitstream DirectX em threads distintas, recomendando async=1, `doNotWait=0`, `enableOutputInVidmem=0`. Código usa `doNotWait=1` quando async (`nvenc_base.cpp:1045`), depois espera evento antes do lock. Isso corresponde ao fluxo genérico §6.1, mas diverge da recomendação DirectX específica. É ponto de investigação controlada, não prova de defeito observado. Não alterar threads nem bloquear indefinidamente sem ensaio de cancelamento/device loss.

### Falhas após submit: teardown precisa provar fim do trabalho

`nvenc_base.cpp:973–977,1038–1055` desmapeia entrada também após timeout do evento ou falha do lock. Retorno vazio faz `encode_nvenc` falhar e loop sair (`video.cpp:3747–3749,5714–5718`); não há retry cego usando mesmo buffer no loop normal. Porém retorno por timeout não confirma conclusão do hardware. `destroy_encoder():894–920` tenta destruir saída/desregistrar recurso e só depois destrói encoder, limpa handles mesmo quando certas operações falham. Não envia EOS explicitamente.

Guia NVIDIA exige EOS antes de fechar e conclusão antes de reutilizar amostras. Risco de recurso ainda em uso/teardown inconsistente é condicional; sem injeção real de timeout/device loss não foi provado use-after-free no driver. Backend CUDA Linux preserva encoder quando destruição falha (`nvenc_cuda.cpp:138–140`); D3D11 usa default que não preserva handle (`nvenc_base.h:104–107`). Não misturar correção de vida útil com ajuste de deadline.

### Fallback de preset perde configuração efetiva conhecida

`nvenc_base.cpp:760–791` pode inicializar H.264/HEVC com `encodeConfig=nullptr` após rejeição da configuração explícita. Nesse caso overrides de CBR, ausência de B frames, lookahead, referências e VBV deixam de estar garantidos pelo código. Mesmo assim `806–810` salva `enc_config` rejeitado como se fosse configuração efetiva, e `set_bitrate():1149–1163` o reenvia. Discrepância de estado é demonstrável por leitura; rejeição posterior/latência adicional depende do driver e do raro fallback. Testes atuais de API/versionamento não cobrem esse cenário. Próxima correção deve rastrear configuração desconhecida e decidir se reconfiguração deve falhar fechada/reconstruir; não inventar parâmetros do preset.

### Evento de completion perde propriedade quando async não suportado

`nvenc_d3d11.cpp:34` cria evento; `nvenc_base.cpp:361–364` zera handle se GPU não suporta async; destrutor `nvenc_d3d11.cpp:42–44` só fecha handle não nulo. Esse ramo perde handle e também impede recuperação de async no mesmo objeto. Vazamento é defeito lógico condicional, não identificado como causa de latência. Sem patch adicional para cumprir limite de uma correção.

### RFI guarda intervalo antes de sucesso

`nvenc_base.cpp:1095–1123` suprime intervalo já registrado, mas atualiza `last_rfi_range` antes de verificar capacidade/todas as invalidações. Após falha, mesmo intervalo pode ser classificado como concluído. Contrato exige IDR após erro (`nvenc_base.h:75–80`); wrapper e loop já carregam fallback (`video.cpp:1320–1323,5527–5558,5651–5656`). Corrigir exige teste de sequência falha, IDR e repetição, sem confundir confirmação protocolar com sucesso parcial. Não mudar RFI nesta correção VBV.

## Captura e pacing: onde existem filas/esperas

- `src/video.h:113` usa `safe::event_t` para imagens, não FIFO. `thread_safe.h:64–78` substitui payload pendente; slow encoder perde frames antigos em vez de acumulá-los indefinidamente.
- `video.cpp:3233–3234` cria pool com 12 imagens: capacidade de recursos não implica fila de 12 frames. `3328–3382` procura imagem livre e dorme 1 ms quando pool cheio; pressão pode atrasar captura. Instrumentar ocupação/idade antes de reduzir pool.
- `display_wgc.cpp:355–379` obtém imagem e mutex do destino antes do mutex IPC, evitando bloquear helper durante espera do pool. `408–419` copia superfície compartilhada e libera IPC logo após enfileirar cópia.
- Caminho direto não fixa frame no momento do evento: `display_vram.cpp:647–675` lê registro sob mutex, usa timestamps da imagem realmente convertida e pula duplicatas. `ipc_session.cpp:638–691` trata evento como aviso e metadado como predicado, evitando sinal auto-reset velho. `claim_latest_frame():840–863` consome frame mais recente, não FIFO de imagens estáveis.
- `display_base.cpp:508–550` tenta slot com snapshot zero e reinicia pacing com snapshot de até 200 ms. WGC transforma zero em graça de até 6 ms (`display_wgc.cpp:128–161`), com clamp por fps somente em modo constante. Em cadências acima de aproximadamente 166 fps, graça de 6 ms pode ultrapassar intervalo; em modo não constante, grupo pode reancorar após fonte atrasada. É risco de pacing dependente da cadência, não justificativa para eliminar graça e perder frames frescos.
- `display_base.cpp:560–599` suaviza reancoragem em janela de até 2 ms/um quarto de frame; `video.cpp:5679–5695` ajusta timestamp de apresentação perto do grid, preservando `capture_timestamp` separado. Esse ajuste não reduz tempo físico de captura/encode.
- `display_base.cpp:601–619` espera extra de 10 ms só após timeout bloqueante, documentada para evitar starvation do lock D3D. Não remover sem demonstrar efeito na reinit.
- `video.cpp:5348–5352` calcula orçamento mínimo de FPS; log divide por 2000 e duplica duração, embora conta use 1000. Mensagem pode levar diagnóstico de cadência pela metade. Não mexido por estar fora do escopo de escrita.
- `nvenc_d3d11_on_cuda.cpp:207–245`: 10-bit 4:4:4 envolve map D3D/CUDA, cópia 2D planar e unmap; não confundir esse caminho com D3D11 nativo NV12/P010. Remover sincronização sem contrato pode corromper pixels.

## Configuração preservada

`nvenc_config.h:49–87`: P1, multipass quarter-resolution, VBV +0%, weighted prediction desligado, AQ espacial/temporal desligados, split auto. `nvenc_base.cpp:384–388,482–501`: ULL, GOP infinito, `frameIntervalP=1`, CBR, zero reorder, lookahead desligado. `config.cpp:1803–1808` valida preset 1–7 e VBV +0–400%; `980–984` conserva minimum FPS 20, smoothing e input direto WGC ativados. Esses defaults não foram alterados. Custo de multipass/AQ e ganhos de preset exigem avaliação de qualidade e contenção GPU, não suposição baseada em nome de preset.

## Matriz de qualidade e latência para RTX 5060

Fontes NVIDIA §§3.3, 3.8.4, 6.4, 8.5, 8.14 e 9. Comparações abaixo são plano de ensaio, não resultados. Codec mais eficiente pode oferecer melhor qualidade no mesmo bitrate, mas não garante menor decode/p99 no POCO. Não confundir recomendações de OBS/broadcast tolerantes a latência com cloud gaming interativo.

| Controle | Estado/efeito documentado | Ensaio seguro, sem mudar default |
| --- | --- | --- |
| Preset P1–P7 | P1 privilegia desempenho; P7 reduz desempenho buscando qualidade. Tuning é escolha separada. Tabelas NVIDIA são throughput agregado de GPUs de referência, não latência medida na RTX 5060. | Comparar P1/P3/P5/P7 com mesma cena/bitrate; promover maior qualidade que preserve margem de encode e cauda. Não extrapolar 1080p 8-bit para resolução nativa 10-bit. |
| LL/ULL vs HQ/UHQ | LL/ULL recomendados para interação; HQ/UHQ não são atalho gratuito para baixa latência. ULL atual evita reordenação/lookahead. | Manter ULL como controle; avaliar LL apenas separadamente com frame sizes/qualidade/filas. UHQ/temporal filtering exigem suporte API/GPU e contrato compatível, não apenas headers novos. |
| VBV | Um frame nominal = bitrate/fps. Aumento permite frames maiores, altera restrição de rate control/HRD, não equivale a fila física. | Manter +0% como controle. Não aumentar para mascarar perda Wi-Fi; comparar artefatos em cenas difíceis e p99 de envio. |
| Multipass | Quarter/full analisam frame atual; não são lookahead de frames futuros. NVIDIA documenta melhor distribuição de bits e custo maior que single-pass. Quarter pode capturar movimento maior; full produz estatísticas melhores. | Comparar quarter atual com full e single em builds/config iguais; medir qualidade e contenção GPU. Não desativar para ganhar latência sacrificando imagem sem autorização. |
| AQ espacial | Redistribui QP, pode melhorar percepção e reduzir PSNR. Usa recursos CUDA. | Comparar qualidade visual/texto/gradientes e cauda sob jogo pesado. PSNR isolado não decide. |
| AQ temporal | Pode melhorar regiões estáticas detalhadas; aumenta variação de bits por frame e usa CUDA. CBR no GOP não implica tamanho constante por frame. | Medir bursts, Wi-Fi/FEC e p99 além de qualidade; não ligar por padrão sem medir. |
| Lookahead/B convencionais | Referências futuras exigem retenção. Código desliga lookahead e usa `frameIntervalP=1`. | Preservar. HEVC unidirectional B do SDK13 é recurso distinto e requer query de capacidade, sinalização e validação decoder; não ativado neste patch. |
| Weighted prediction | Recurso de qualidade com custo/restrições; incompatível com split-frame HEVC segundo NVIDIA. | Só testar se query/runtime/API e modo de sessão permitem; default off preservado. |
| Split-frame | Divide um frame entre vários NVENCs, não cria engines. RTX 5060 desktop tem um NVENC. | Não forçar; ganho multi-engine não se aplica a este modelo. |
| 10-bit/HDR/chroma | AV1 Main suporta 4:2:0 8/10-bit no encoder; Blackwell também expõe recursos extras H.264/HEVC. Aceitação end-to-end é requisito separado. | 4:2:0 primeiro; Main10/HDR só com decoder/cores validados. Não forçar 4:2:2/4:4:4 por existir no host. 10-bit SDR pode reduzir banding mas exige comparação e cliente compatível. |
| GOP/recovery | GOP infinito evita IDR periódico por design; perda pede RFI/IDR. IDR pode criar burst. | Preservar recuperação e testar reconexão/perda; não remover FEC/IDR para melhorar benchmark sem perdas. |

NVENC dedicado não significa custo zero no restante da GPU: conversão/cópias, AQ, multipass e outros recursos podem competir. Reduzir saturação do jogo preservando qualidade pode reduzir caudas; necessidade de margem deve ser medida, não fixada em percentual inventado. Uma sessão apenas no baseline; gravação/streams paralelos compartilham engine e recursos.

## Testes executados e limites

Compilador encontrado sem instalação: `C:\Program Files\Windhawk\Compiler\bin\clang++.exe`, clang 20.1.3, alvo padrão i686 MinGW. Link estático evita DLLs runtime ausentes. Artefatos em diretório temporário aprovado; nenhum executável de teste chama APIs GPU.

1. Regressão nova: passou com clang 20.1.3 e também com wrapper C++ local (Zig 0.14.1 fornecido pela sessão principal). Método extraído do baseline exato `5e6ed8594184212d429bb093d7fd47904c9a6fa6` via `git show`, em árvore temporária: falha esperada no assert 8.333 bits. Variante temporária reinjetando piso antigo também falhou no mesmo assert.
2. `test_nvenc_api.cpp` + `test_nvenc_event_wait.cpp`, fontes reais `nvenc_event_wait.cpp`, googletest/googlemock inicializados: **18/18 passaram**.
3. `test_wgc_monitor_selection.cpp` com googletest: **29/29 passaram**, incluindo publicação WGC e identidade/deduplicação direta.
4. Regressão `test_keyed_mutex_abandoned.py`: passou com Zig 0.14.1 e clang 20.1.3; baseline e três reintroduções isoladas falharam como esperado.
5. `git diff --check`: passou.

Comando da regressão (PowerShell; variáveis só no processo):

```powershell
$python = (Get-Command python).Source
$cxx = (Get-Command clang++).Source
& $python tests/unit/test_nvenc_bitrate.py --compiler $cxx --compiler-arg=-static
& $python tests/unit/test_keyed_mutex_abandoned.py --compiler $cxx
```

Compilação CPU dos 18 testes existentes:

```powershell
& 'C:\Program Files\Windhawk\Compiler\bin\clang++.exe' -std=c++20 -static -I. -Ithird-party/googletest/googletest/include -Ithird-party/googletest/googlemock/include -Ithird-party/googletest/googletest -Ithird-party/googletest/googlemock -Ithird-party/nv-codec-headers/include tests/unit/test_nvenc_api.cpp tests/unit/test_nvenc_event_wait.cpp src/nvenc/nvenc_event_wait.cpp third-party/googletest/googletest/src/gtest-all.cc third-party/googletest/googlemock/src/gmock-all.cc third-party/googletest/googletest/src/gtest_main.cc -o "$env:TEMP\nvenc-existing-tests.exe"
& "$env:TEMP\nvenc-existing-tests.exe"
```

Leitura de `tests/unit/test_video_rfi_retry.py`, políticas WGC e API/versionamento não equivale a executar aplicação. Build completo não executado: dependências recursivas incompletas e toolchain principal não configurada. Testes CPU i686 não validam build final x64, DirectX, CUDA, codificação real ou firmware. Runners independentes registrados no bloco GNU/Clang de `tests/CMakeLists.txt` como `test_component_nvenc_bitrate` (sessão principal) e `test_component_keyed_mutex_abandoned` (expansão autorizada). CTest completo não executado. Nenhum p50/p95/p99 de hardware ou ganho percentual afirmado.

## Fontes oficiais

Consultas Context7: `/websites/nvidia_video-technologies_video-codec-sdk` (async, VBV/reconfigure, weighted prediction); `/websites/learn_microsoft_en-us_windows_win32_api` (keyed mutex). Referência Microsoft específica e guia NVIDIA 13.1 lidos diretamente quando índice foi insuficiente/desatualizado.

- [NVIDIA Video Codec SDK 13.0, guia de programação](https://docs.nvidia.com/video-technologies/video-codec-sdk/13.0/nvenc-video-encoder-api-prog-guide/index.html): §§6.1, 8.4, 9; VBV de um frame = bitrate/framerate, reconfiguração e amostras assíncronas.
- [NVIDIA Video Codec SDK 13.1, guia de programação](https://docs.nvidia.com/video-technologies/video-codec-sdk/13.1/nvenc-video-encoder-api-prog-guide/index.html): §§3.9, 4.4, 5, 6.1–6.3; buffers, fim de sessão, threads e recomendação DirectX específica. Headers locais são SDK13.0 (`third-party/nv-codec-headers/include/ffnvcodec/nvEncodeAPI.h:118–119`).
- [Microsoft: IDXGIKeyedMutex::AcquireSync](https://learn.microsoft.com/en-us/windows/win32/api/dxgi/nf-dxgi-idxgikeyedmutex-acquiresync): `WAIT_ABANDONED` exige recriar superfície/mutex; ordem de espera não garantida.
- [NVIDIA: RTX 5060 family](https://www.nvidia.com/en-us/geforce/graphics-cards/50-series/rtx-5060-family/): specs fornecidas pela sessão principal; um NVENC de nona geração na RTX 5060. Não extrapolar características de placas Blackwell com vários NVENCs.
