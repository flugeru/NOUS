# POCO X6 Pro / Dimensity 8300-Ultra: decoder e interoperabilidade com RTX 5060 NVENC

Pesquisa documental: **2026-11-14**. Escopo: streaming GameStream de Vibepollo para Moonlight Android / Artemis Android. Não altera configuração, preferência, codec automático ou default. Nenhum aparelho ou GPU foi medido nesta pesquisa.

## 1. Conclusão operacional

- **Decoder fica no cliente Android.** RTX 5060 no host usa **NVENC para encode**; NVDEC do host não substitui MediaCodec do telefone neste caminho.
- MediaTek anuncia playback H.264, HEVC, VP9 e AV1 no **Dimensity 8300**, incluindo frase explícita **“4K AV1 media decoding”**. Isso fundamenta investigar AV1, não afirmar perfil, profundidade, fps ou latência efetivos do POCO X6 Pro.
- Xiaomi/POCO confirma **Dimensity 8300-Ultra**, painel **2712 × 1220, até 120 Hz**. Especificação pública de vídeo lista contêineres, não matriz de decoders/perfis. **120 Hz de painel não prova 120 fps de decode**; gravação da câmera não mede reprodução.
- NVIDIA anuncia RTX 5060 desktop Blackwell com **um NVENC de nona geração**, encode H.264/HEVC/AV1. Interoperabilidade exige interseção entre bitstream realmente emitido pelo host, negociação do cliente, capacidades do componente MediaCodec e apresentação por Surface.
- **Não declarar AV1 vencedor nem descartá-lo.** Manter seleção atual; comparar H.264, HEVC e AV1 apenas por testes explícitos, reversíveis, com codec realmente negociado registrado.
- **Desconhecido:** nomes dos componentes no aparelho, hardware/software efetivo, perfis/levels/tiers, 8/10-bit, chroma, desempenho sustentado por resolução/fps, suporte efetivo a low-latency, buffers internos, firmware e versão do APK instalado.

## 2. Hierarquia de evidência

| Nível | O que demonstra | O que não demonstra |
| --- | --- | --- |
| Fabricante do SoC/GPU | Capacidade anunciada da família | Exposição pelo firmware/APK, configuração aceita, desempenho real |
| Ficha do telefone | SoC, painel e recursos comercializados | Matriz MediaCodec ou fps de decode |
| MediaCodecList no telefone | Componentes e capacidades **declaradas pelo firmware** | Correção de todas declarações ou execução dentro do orçamento |
| `configure()`/`start()` e frames válidos | Configuração aceita e decodificação funcional naquele ensaio | Ausência de buffering, throughput sustentado, tempo até fóton |
| Logs, Perfetto, métricas por frame | Filas e tempos observáveis naquela sessão | Estágios internos ocultos do firmware; latência física sem medição externa |
| Câmera rápida / fotodiodo com estímulo controlado | Input-to-photon ou glass-to-glass conforme montagem | Separação automática entre rede, encode, decode e scanout |

**Termos usados:** “evidência” significa fonte consultada ou trecho de código identificado; “hipótese” precisa de ensaio; “desconhecido” significa sem coleta do equipamento. Número anunciado não vira resultado medido.

## 3. Capacidades anunciadas, sem extrapolação

### 3.1 Xiaomi / POCO e MediaTek

**E1 — Ficha oficial POCO**, lida integralmente no domínio oficial [P1]:

- SoC: Dimensity 8300-Ultra; GPU Mali-G615.
- Tela: 2712 × 1220, AdaptiveSync, **até** 120 Hz; HDR10+.
- Campo `Video`: `MP4 | MKV | AVI | WMV | WEBM | 3GP | ASF`. São contêineres; não comprovam codec, perfil ou decoder por hardware.
- Câmera traseira: gravação 4K a 24/30 fps e 1080p a 30/60 fps. **Não usar esses limites como teto ou piso de decode.**
- HyperOS anunciado; versão atual do sistema/driver do exemplar continua desconhecida.

A URL Xiaomi global [P2] e FAQ [P3] apareceram na busca oficial, mas fetch direto retornou HTTP 403. Para dados acima foi usada ficha POCO [P1], acessível. Não atribuir ao corpo bloqueado dados não lidos.

**E2 — MediaTek**, página de produto Dimensity 8300 [M1]:

- `Video Playback`: H.264, HEVC, VP9, AV1.
- Seção MiraVision 880: **“4K AV1 media decoding”**.
- `Video Encoding`: H.264 e HEVC; esse campo não limita encode da RTX nem comprova capacidade de decode por perfil no telefone.
- Referência a **4K60 HDR** aparece na seção **captura/ISP**, não é licença para afirmar AV1 decode 4K60.
- Limites de display FHD+ 180 Hz / WQHD+ 120 Hz pertencem ao SoC; ficha do telefone é referência para painel implementado.

**E3 — Ponte entre modelo e SoC:** artigo oficial MediaTek sobre POCO X6 Pro [M2] identifica Dimensity 8300-Ultra e tela 120 Hz 2712 × 1220. Não publica matriz de perfis/levels ou benchmarks MediaCodec. O sufixo **Ultra** não permite inventar diferenças nem identidade completa dos limites de mídia em relação ao 8300 genérico.

**Hipótese H1:** implementações por hardware dos codecs anunciados podem estar disponíveis via componentes MediaTek no firmware. Só `MediaCodecList` e execução identificada confirmam quais. Nomes `c2.mtk.*` / `OMX.MTK.*` encontrados no código cliente são heurísticas por prefixo, **não inventário deste telefone**.

### 3.2 RTX 5060 e host

**E4 — Matriz oficial NVIDIA [N1]:** linha **GeForce RTX 5060**, não linha Ti ou notebook, informa Blackwell, NVENC nona geração e um encoder. Marca suporte de encode H.264, HEVC e AV1; recursos adicionais como 4:2:2/4:4:4/10-bit no host não demonstram aceitação pelo cliente. Família oficial [N2] também anuncia nona geração NVENC.

**E5 — Código local Vibepollo**, `src/nvenc/nvenc_base.cpp`, consultado nesta árvore:

- Linha 385: `NV_ENC_TUNING_INFO_ULTRA_LOW_LATENCY`.
- Linhas 484–487: `frameIntervalP = 1`, `zeroReorderDelay = 1`, `enableLookahead = 0`.
- Linhas 489–500: multipass/AQ configuráveis e VBV calculado quando capacidade permite; não inferir valores atuais sem configuração/logs.
- Inicialização consulta capacidades e pode negociar versões de API. A presença do código não prova que binário em execução é esta revisão nem que sessão selecionou NVENC.
- `src/rtsp.cpp` lê `x-nv-vqos[0].bitStreamFormat` e valida modos HEVC/AV1. Perfil/chroma/HDR precisam acompanhar negociação efetiva.

Guia oficial NVENC [N3] documenta escolhas de tuning, reordenação e lookahead. Não trocar modo para UHQ ou aumentar filas porque GPU anuncia mais ferramentas de qualidade: são objetivos diferentes. Sem benchmark, não prometer redução de encode nem decode.

**Interseção necessária, por sessão:**

`codec + perfil/level/tier + resolução + fps + bitrate + bit depth + chroma + sinalização de cor/HDR + referências + recuperação de perdas`.

O stream não depende de contêiner MP4/MKV de ficha comercial. Transporte GameStream entrega unidades comprimidas ao decoder; compatibilidade de player de arquivos não garante caminho interativo equivalente.

## 4. O que Android permite consultar — e limites

Documentação Android consultada via **Context7 obrigatório**, biblioteca `/websites/developer_android`, três consultas focadas: descoberta/capacidades; low-latency/Surface; Perfetto. Fontes oficiais resolvidas estão em [A1–A9]. Context7 retornou também trechos Media3; não confundir wrapper Media3 com API de plataforma usada por Moonlight. Referências abaixo priorizam `android.media`.

- `new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()` enumera componentes; excluir `isEncoder()`. Registrar aliases e preferir identidade canônica onde API permite. `REGULAR_CODECS` ajuda comparar componentes regulares; `ALL_CODECS` pode incluir variantes que não servem ao stream normal.
- Para cada MIME `video/avc`, `video/hevc`, `video/av01`, registrar `getCapabilitiesForType()`: `profileLevels`, `colorFormats`, recursos supported/required, `getMaxSupportedInstances()`, `VideoCapabilities` e `isFormatSupported(format)` para candidato concreto. Não interpretar `colorFormats` de buffers CPU como prova completa de caminho Surface/HDR.
- Desde API 29, `isHardwareAccelerated()`, `isSoftwareOnly()`, `isVendor()` ajudam classificação. Android avisa que atributo de aceleração é fornecido pelo fabricante e **não pode ser verificado pela própria API** [A2]. Nome e flag são evidência declarativa, não medição do bloco físico.
- `areSizeAndRateSupported(w,h,fps)` e ranges indicam admissibilidade declarada; **não são benchmark de baixa latência**. Não multiplicar máximos independentes de largura, altura e fps.
- `getSupportedPerformancePoints()` (API 29) descreve pontos de desempenho publicados. Registrar ausência/null/lista vazia como recebido, sem inventar ponto. `PerformancePoint.covers()` ajuda triagem, não garante cauda de latência, temperatura ou apresentação daquela aplicação.
- `getAchievableFrameRatesFor(w,h)` (API 23) pode retornar null; é estimativa do fabricante, não garantia sustentada para qualquer bitstream/perfil.
- `CodecCapabilities.FEATURE_LowLatency` e `MediaFormat.KEY_LOW_LATENCY` são API 30. Se recurso anunciado, `KEY_LOW_LATENCY=1` pede modo que não retenha dados além do necessário pelo padrão. AOSP exige driver do parceiro e observa custo de permanecer acordado [A7]. Não elimina dependências do bitstream, filas da rede, SurfaceFlinger nem scanout.
- `KEY_PRIORITY=0` é prioridade real-time do codec; não promessa de deadline nem prioridade arbitrária de thread. `KEY_OPERATING_RATE` é dica de taxa de operação para planejamento de recursos; `Short.MAX_VALUE` usado em clientes não significa 32767 fps alcançados.
- `getSupportedVendorParameters()` (API 31) permite inventário de parâmetros publicados. Ausência de chave não prova inexistência de todo mecanismo proprietário; presença não prova benefício. `MediaFormat.setInteger()` apenas inserir valor no mapa **não demonstra** que driver conhece/aplica chave.
- `releaseOutputBuffer(index,false)` devolve sem apresentar; overload com timestamp pede entrega à Surface no tempo indicado; overload boolean true pede render. Nenhum deles prova emissão imediata de luz. Timestamps futuros podem adicionar espera; timestamp zero e “agora” têm políticas específicas observadas no cliente, não devem ser tratados como equivalentes universais.
- `OnFrameRenderedListener` (API 23) pode ajudar correlação, mas callback pode atrasar/agrupar. Não é fotodiodo nem substitui prazo real de display [A5].

## 5. Evidência do código cliente: filas e pacing

Snapshots públicos identificados por SHA, consultados em 2026-11-14. **Não afirmam versão instalada.** Links imutáveis em [C1–C6].

| Projeto | Revisão consultada | Arquivos |
| --- | --- | --- |
| Moonlight Android | `b48494cb96bff23d8886c4775cc4f39a1075495d` | `MediaCodecDecoderRenderer.java`, `MediaCodecHelper.java` |
| Artemis Android, `ClassicOldSong/moonlight-android`, branch `moonlight-noir` | `c5cf27f4dc822db0e863c4691e7a70c74bea977a` | mesmos arquivos |
| moonlight-common-c upstream | `94c65fd364d17b223f00a8763c9a5d94a66cb8dd` | `VideoDepacketizer.c`, `VideoStream.c`, `Limelight.h` |

### 5.1 Moonlight Android

**E6:** renderer [C1], `findAv1Decoder()` linhas 240–269, retorna sem buscar AV1 salvo `FORCE_AV1` nessa revisão. Logo, “automático usa AV1 porque hardware tem AV1” é afirmação falsa para esse código. APK/fork diferente exige nova inspeção. Seleção HEVC usa whitelist, pedido explícito/HDR e triagem de performance points.

**E7:** helper [C2], `setDecoderLowLatencyOptions()` linhas 495–594:

- Tentativa inicial usa `low-latency=1`; com `FEATURE_LowLatency`, retorna sem adicionar outros hacks na tentativa zero.
- Fallback tenta `vdec-lowlatency`, comenta rota histórica MediaTek OMX e exceção para Xiaomi antigo. Isso não certifica implementação Codec2 do POCO atual.
- Dicas operating-rate/prioridade e extensões por fornecedor têm tentativas progressivamente menos arriscadas. Primeiro `configure()` aceito não prova todas chaves aplicadas.
- Busca de decoder dá preferência a componentes com recurso low-latency antes de fallback. Lista global não basta: registrar componente realmente criado e opções de tentativa vencedora.

**E8:** renderer [C1], linhas 947–1085:

- `FRAME_PACING_BALANCED` usa thread `Choreographer`; fila Java tem `OUTPUT_BUFFER_QUEUE_LIMIT = 2`. Callback libera até um frame quando devido; produtor descarta output mais antigo se limite atingido.
- Fora de Balanced, drena outputs já disponíveis e conserva mais recente. `MAX_SMOOTHNESS`/`CAP_FPS` usam timestamp zero; outro caminho usa `System.nanoTime()`, com comentários sobre drops no mesmo VSync.
- Limite **2 é fila Java de outputs decodificados**, não total de buffers do decoder, BufferQueue ou pipeline inteiro. Não equivale a atraso fixo de dois frames.
- `dequeueOutputBuffer(...,50000)` tem timeout **máximo de espera sem output**, não sleep obrigatório de 50 ms por frame.
- Contador `decoderTimeMs` usa diferença entre uptime e PTS nessa região. Métrica precisa ser lida com semântica do PTS; não é automaticamente input-to-photon.

### 5.2 Artemis Android

**E9:** helper [C4], linhas 533–673, recebe `ultraLowLatency`; quando ativado, evita retorno antecipado após recurso padrão e pode adicionar conjunto de chaves MediaTek por prefixo. Exemplos: `vendor.mtk.vdec.low-latency.mode`, `vendor.mtk.vdec.input.max.queue.depth`, `vendor.mtk.vdec.output.max.queue.depth`, boost/DVFS e guard/timeout.

**Não homologar essas chaves:** comentários “safe & balanced” no código são afirmações do autor, não documentação oficial MediaTek nem ensaio do POCO. `safeSet()`/ausência de exceção na escrita do formato não valida implementação no driver. Não copiar lote de chaves para host ou torná-lo default.

**E10:** renderer [C3]:

- `preferLowerDelays` inicia false; nomes/comentários não bastam para interpretar caminho. Bloco `LATEST_ONLY_LOW_LATENCY` nas linhas 1212–1247 executa sob **`!preferLowerDelays`** e drena outputs sem bloquear, descartando anteriores antes de liberar último.
- `releaseWithPolicy()` linhas 67–81 usa boolean true quando `preferLowerDelays` e timestamp até 300 µs à frente; em outros casos usa timestamp; há fallback. Isso não mede apresentação física imediata.
- Thread renderer usa `THREAD_PRIORITY_URGENT_DISPLAY`; várias heurísticas dependem de período da tela/stream. Mudança de prioridade pode alterar consumo/competição; não prova ganho.
- `updateDecodeLatencyStats()` linhas 85–98 usa mapa PTS para tempo de enqueue e calcula enqueue→observação do output; `USE_FRAME_RENDER_TIME=false` nessa revisão. Overlay não cobre automaticamente fila Surface/display. Comparar médias de Artemis e Moonlight sem igualar definições é inválido.
- Fila Balanced de limite 2 ainda existe, mas caminho latest-only pode consumir/liberar outputs antes desse ramo. Não afirmar comportamento idêntico ao upstream só porque classe tem mesmo nome.

**Hipótese H2:** diferença percebida entre APKs pode vir de pacing, descarte de output e definição de métrica, não de bloco decoder diferente. Confirmar com mesmo stream, rastreio e versão dos APKs.

### 5.3 moonlight-common-c e árvore local

**E11:** common upstream [C5–C6] e arquivos locais `third-party/moonlight-common-c/src/{VideoDepacketizer.c,VideoStream.c,Limelight.h}` mostram camada de transporte/depacketização e entrega de decode units; **não implementam decoder MediaCodec**.

- `CAPABILITY_DIRECT_SUBMIT`: callback direto, sem fila intermediária de decode units desse caminho. Não pula buffers internos de MediaCodec ou Surface.
- `CAPABILITY_PULL_RENDERER`: aplicação retira frame e deve concluir com `LiCompleteVideoFrame()`; não pode ser combinada com direct-submit. Outro caminho usa thread de entrega.
- `decodeUnitQueue` é inicializada com limite 15 nos códigos consultados; capacidade máxima não significa prebuffer obrigatório de 15 frames.
- Na árvore local, `VideoDepacketizer.c` linhas 512–538 trata overflow: registra `Video decode unit queue overflow`, limpa fila/estado e pede IDR; direct-submit chama renderer diretamente. Upstream citado tem evolução e linhas diferentes; não presumir SHA local igual ao upstream nem common embutido no APK igual à cópia do host.
- Descartar **output já decodificado** para renderizar mais recente é diferente de descartar frames comprimidos de referência antes do decode. Este último pode exigir recuperação IDR/RFI e comprometer imagem. Não reduzir filas cegamente nem forçar flags de RFI com base apenas no nome do SoC.

## 6. Coleta segura: aparelho real

**Aviso de privacidade:** dumps e traces podem conter modelo, fingerprint, nomes de apps, camadas de janela e outros identificadores. Colete em máquina confiável, revise e remova dados pessoais antes de compartilhar. Autorize USB debugging manualmente somente para computador confiável. Não habilite ADB pela rede para executar este plano.

### 6.1 Coletor PowerShell somente leitura

Arquivo: [`../../tools/latency/Collect-AndroidDiagnostics.ps1`](../../tools/latency/Collect-AndroidDiagnostics.ps1). Requer Platform Tools já instalado, aparelho já autorizado e serial explícito. `adb` não estava disponível no PATH desta pesquisa; nenhuma coleta foi executada.

```powershell
adb devices -l
.\tools\latency\Collect-AndroidDiagnostics.ps1 -Serial 'SERIAL_DO_APARELHO' -OutputDirectory '.\android-before'
```

Coletor lê propriedades selecionadas, `dumpsys -l`, `media.codec`, `media.player`, `media.metrics`, `display`, `thermalservice`, `battery`, lista de layers SurfaceFlinger, categorias atrace e ajuda Perfetto. Cada processo tem timeout de 20 s; dumps individuais pedem timeout de 10 s. Salva stdout, stderr, exit code e timeout em diretório local novo, sem sobrescrever diretório existente.

Não instala APK, não usa root, não muda `settings`/`setprop`, não limpa logcat, não inicia trace, não reinicia serviço nem força modo de tela. ADB pode iniciar seu daemon local; “somente leitura” refere-se a comandos enviados ao aparelho e ausência de alterações de configuração. Falha de serviço ou `Permission denied` permanece como evidência de cobertura limitada. Não tentar contornar permissão.

**Limitação importante:** `dumpsys media.codec` pode faltar, estar vazio ou expor só estado parcial/instâncias ativas. Não é substituto universal de `MediaCodecList`; `adb shell dumpsys codec` não é nome portátil de serviço. Consultar `dumpsys -l` e guardar resultado, sem tratar ausência de serviço como ausência de decoder.

Execute uma coleta sem stream e outra durante stream estável, em diretórios diferentes. Registre horário UTC, APK/package/versionCode, firmware, modo de tela observado, codec negociado, resolução/fps/bitrate/HDR/pacing e estado térmico. Não automatizar início ou alteração do stream neste coletor.

**Check reproduzível de sintaxe, sem ADB:** executado com sucesso em Windows PowerShell 5.1 nesta pesquisa.

```powershell
$tokens = $null
$errors = $null
$path = (Resolve-Path '.\tools\latency\Collect-AndroidDiagnostics.ps1').Path
$null = [System.Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw ($errors | Out-String) }
'PASS: sintaxe PowerShell'
```

Teste de sintaxe não valida firmware, disponibilidade dos serviços nem resultados reais. Execução no aparelho continua pendente.

### 6.2 MediaCodecList: probe dentro de app de diagnóstico ou build instrumentada

Sem APK nesta entrega; instalação altera estado do telefone e deve ser autorizada separadamente. ADB shell puro não executa diretamente API Java pública `MediaCodecList`. Não fornecer comando fictício `adb shell MediaCodecList`.

1. Obtenha consentimento para probe mínimo ou build instrumentada do cliente. Não substituir APK pessoal sem preservar dados e versão. Não pedir root.
2. Exporte JSON com fingerprint/SDK, nome/canonical/alias de cada decoder, MIME, flags hardware/software/vendor, perfis/levels crus e decodificados por constantes, features e parâmetros de fornecedor publicados. Preserve exceções por componente.
3. Exporte ranges de bitrate/resolução/fps, alinhamentos, performance points, achievable rates e resultado de candidato concreto em `isFormatSupported()` e `areSizeAndRateSupported()`. Valores nulos não viram zeros. Não deduzir chroma/bit depth só do nome MIME.
4. Para candidatos selecionados, crie componente pelo nome com `MediaCodec.createByCodecName()` e use Surface. Capture formato solicitado, tentativa efetiva, `getInputFormat()`/`getOutputFormat()` e eventos de erro/format-change. Não rodar todos decoders em paralelo: recursos compartilhados distorcem resultado.
5. Decodifique bitstream compatível com o realmente emitido pelo host; registre perfil, level/tier, chroma, profundidade, referências e sinalização HDR a partir de headers/metadata. Só depois rode sessão GameStream real com mesmo candidato.
6. Instrumente por frame: receive/depacketize, espera de input, queueInputBuffer, output disponível, releaseOutputBuffer, callback render quando houver; registre PTS, relógio monotônico, drops e ocupação de filas. Não subtrair relógio host de relógio telefone sem sincronização/limite de erro.

Coletar capacidade declarada **antes** de qualquer alteração experimental; repetir após OTA/atualização de APK. Não depender de `media_codecs.xml` como inventário completo: configuração pode ser dividida em partições/arquivos e comportamento de driver não se esgota em XML.

### 6.3 Perfetto e apresentação

Captura Perfetto é etapa **separada e explícita**, não “somente leitura sem efeito”: grava arquivo no telefone, ativa tracing por intervalo e tem overhead. Confirmar consentimento e espaço. Não alterar configurações permanentes ou usar root.

1. Consulte `adb -s SERIAL shell atrace --list_categories` e `adb -s SERIAL shell perfetto --help`. Use somente categorias disponíveis; preserve erros de acesso.
2. Faça trace curto durante sessão já estabilizada. Exemplo de light mode oficial adaptado para serial; remova categoria não disponível:

   ```powershell
   adb -s SERIAL shell perfetto --time 15s --buffer 32mb sched gfx view wm --out /data/misc/perfetto-traces/poco-stream-01.perfetto-trace
   adb -s SERIAL pull /data/misc/perfetto-traces/poco-stream-01.perfetto-trace .\poco-stream-01.perfetto-trace
   ```

3. Para FrameTimeline, use configuração explícita com fonte `android.surfaceflinger.frametimeline` quando disponibilizada (Android 12+), além de `linux.ftrace` com categorias/eventos permitidos. O comando light mode acima **não promete habilitar FrameTimeline**. Adicione categoria `video` apenas se listada. App trace markers podem exigir build debuggable/profileable/permissões; não presumir visibilidade de APK release.
4. Inspecione thread de recepção/submit, renderer, Choreographer, escalonamento CPU, esperas/fences/BufferQueue visíveis, SurfaceFlinger e expected/actual presentation das layers relevantes. A própria documentação [A9] declara **SurfaceViews atualmente não suportadas**. Não usar FrameTimeline como cobertura garantida dos frames de vídeo de Moonlight/Artemis; conferir tipo de Surface e versão do sistema. Trilhas da interface e do compositor ainda podem ajudar diagnóstico, sem substituir instrumentação do vídeo.
5. Correlacione eventos do cliente com PTS/frame ID e logs host. Separe **queue→output**, **output→release**, **release→apresentação observável** e perdas; partes ocultas ficam desconhecidas. Trace sem símbolos/eventos do vendor não permite medir tempo exato do bloco físico decoder.
6. Compare ensaio com e sem tracing para estimar perturbação. Para input-to-photon, use estímulo e câmera rápida/fotodiodo; não somar médias de overlays diferentes nem interpretar evento Surface como fóton.

Referências oficiais: Perfetto Android [A8], FrameTimeline [A9]. Não publique trace bruto sem revisar privacidade. Não remover automaticamente evidências do aparelho; limpeza, se desejada, fica manual e fora do coletor.

## 7. Seleção de codec: qualidade com baixa latência, sem mudar default

**Regra:** manter configuração corrente como controle. Ordem abaixo organiza experimento; não muda prioridade de negociação do produto.

| Candidato | Por que testar | Condição antes de aceitar | O que não afirmar |
| --- | --- | --- | --- |
| H.264 SDR 8-bit 4:2:0 | Referência de interoperabilidade, se disponível no aparelho | Decoder/stream efetivos, qualidade e caudas dentro do objetivo | “Sempre menor latência” |
| HEVC SDR; Main10/HDR em ensaio separado | Outra curva qualidade/bitrate; caminho HDR quando compatível | Perfil/bit depth reais, cores corretas, sem fallback inesperado | “Metade do bitrate” ou “mais rápido” sem medir |
| AV1 SDR; 10-bit/HDR só após probe | Há anúncio MediaTek e encode NVIDIA; merece ensaio | Componente efetivo, perfil, fps sustentado, pacing e custo térmico validados | “8300-Ultra garante AV1 120 fps” ou “AV1 é default” |
| VP9 | Inventário pode listar por anúncio do SoC | Fora da seleção normal H.264/HEVC/AV1 deste caminho | “Playback VP9 implica NVENC VP9/GameStream VP9” |
| 4:2:2 / 4:4:4 e outros codecs | Só requisito explícito e suporte end-to-end demonstrado | Cliente e negociação precisam suportar, não apenas GPU | Disponibilidade automática no Android por suporte RTX |

### Protocolo de comparação

1. Registre controle atual antes de tocar nas opções; mantenha cópia dos valores, não faça reset global. Primeiro fixe resolução/fps **já confirmados** no aparelho, HDR desligado e mesmo pacing. Não escolher 120 fps ou resolução nativa somente pela ficha do painel.
2. Use mesma cena reproduzível, câmera/movimento/texto, captura host, driver, rede, brilho/modo de tela e temperatura inicial. Capture codec/perfil negociado: selecionar opção na UI não prova codec ativo nem ausência de fallback.
3. Compare um codec por vez com bitrate igual para qualidade; depois encontre bitrate mínimo de cada um para critério visual equivalente. Compare também p50/p95/p99 dos estágios e drops. Qualidade equivalente pode exigir bitrates diferentes; bitrate igual não é qualidade igual.
4. Repita ordem alternada, com aquecimento e período sustentado suficiente para observar throttling; registre duração e várias repetições. Teste sem trace para resultados principais e com trace para diagnóstico. Não extrair p99 confiável de amostra curta.
5. Mantido codec, teste pacing separadamente: Balanced contra modo que prioriza latência, conforme APK. Depois teste apenas toggle low-latency exposto e reversível pelo cliente, se autorizado. Não misture mudança de codec, fps, bitrate, fila e pacote de chaves vendor na mesma comparação.
6. Para target de `F` fps, orçamento nominal entre frames é `1000/F` ms. Throughput que acompanha F é necessário, mas não prova baixa latência: decoder pode acumular fila. Registre idade do frame e profundidade/backpressure, não apenas fps médio.
7. Rejeite candidato com artefatos persistentes, erros, recuperação/IDR excessiva, fallback não planejado, cor/HDR incorretos, caudas ou quedas incompatíveis com objetivo. Escolha por qualidade percebida **sob restrição medida de latência e estabilidade**, não por geração do codec.
8. Restaure controle após cada ensaio. Só propor mudança de default em decisão separada com dados reproduzíveis, matriz de firmware/APK e rollback. Esta pesquisa não propõe tal mudança.

### Registro mínimo por ensaio

```text
id, UTC, fingerprint, SDK, APK/package/versionCode, build host/driver
codec solicitado/negociado, componente/canonical, aceleração declarada
perfil/level/tier, bit depth/chroma, SDR/HDR e cor
resolução, fps pedido/observado, refresh ativo, bitrate pedido/observado
pacing, low-latency pedido/anunciado, tentativa/formatos efetivos
rede/perdas/FEC, recuperação IDR/RFI, drops por estágio
queue->output p50/p95/p99, output->release, apresentação observável
idade do frame, qualidade/cena, temperatura/throttling, duração/amostras
cobertura do trace, clock/erro de sincronização, falhas e dados ausentes
```

## 8. Perguntas ainda abertas

- Qual ROM/região, Android/HyperOS/patch, componente e versão de APK estão no POCO do usuário?
- Quais MIME/perfis/levels e recursos low-latency aparecem em `MediaCodecList`? Componente usado coincide com candidato inventariado?
- AV1 exposto é acelerado conforme flags do firmware e funciona no stream específico? Qual envelope resolução/fps/perfil é estável, com margem térmica?
- Quais parâmetros padrão/vendor são reconhecidos e têm efeito observável, versus aceitos/ignorados?
- Qual profundidade real de filas em cada etapa e qual semântica dos números apresentados no overlay instalado?
- Qual perfil/chroma/bit depth o host emite, e quanto custam encode, rede, decode e apresentação separadamente?
- Qual codec oferece melhor qualidade no limite **medido** de latência deste uso? Nenhuma resposta numérica disponível sem ensaio.

## 9. Fontes e URLs

Todas consultadas em 2026-11-14; páginas mutáveis podem mudar. Links GitHub abaixo fixam revisão.

### Fabricantes

- **[P1] POCO oficial — ficha POCO X6 Pro, conteúdo lido:** https://www.po.co/global/product/poco-x6-pro/specs/
- **[P2] Xiaomi global — specs, indexada em busca; fetch HTTP 403:** https://www.mi.com/global/product/poco-x6-pro/specs
- **[P3] Xiaomi FAQ — major selling points, indexada; fetch HTTP 403:** https://www.mi.com/global/support/faq/details/KA-242788/
- **[M1] MediaTek — Dimensity 8300, playback e seção AV1:** https://www.mediatek.com/products/smartphones/mediatek-dimensity-8300
- **[M2] MediaTek — POCO X6 Pro powered by Dimensity 8300-Ultra:** https://www.mediatek.com/tek-talk-blogs/poco-x6-pro-powered-by-dimensity-8300-ultra
- **[N1] NVIDIA — Video Encode and Decode Support Matrix:** https://developer.nvidia.com/video-encode-decode-gpu-support-matrix ; tabela textual oficial lida: https://developer.nvidia.com/video-encode-decode-support-matrix.md
- **[N2] NVIDIA — GeForce RTX 5060 Family:** https://www.nvidia.com/en-sg/geforce/graphics-cards/50-series/rtx-5060-family/
- **[N3] NVIDIA — NVENC Video Encoder API Programming Guide 13.0:** https://docs.nvidia.com/video-technologies/video-codec-sdk/13.0/nvenc-video-encoder-api-prog-guide/index.html

### Android / Perfetto oficiais

- **[A1] MediaCodecList:** https://developer.android.com/reference/android/media/MediaCodecList
- **[A2] MediaCodecInfo:** https://developer.android.com/reference/android/media/MediaCodecInfo
- **[A3] CodecCapabilities:** https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities
- **[A4] VideoCapabilities:** https://developer.android.com/reference/android/media/MediaCodecInfo.VideoCapabilities
- **[A5] MediaCodec — Surface, parâmetros, render callbacks:** https://developer.android.com/reference/android/media/MediaCodec
- **[A6] MediaFormat — KEY_LOW_LATENCY, KEY_PRIORITY, KEY_OPERATING_RATE:** https://developer.android.com/reference/android/media/MediaFormat
- **[A7] AOSP — Low-latency decoding in MediaCodec:** https://source.android.com/docs/core/media/low-latency-media
- **[A8] Android Developers — perfetto CLI:** https://developer.android.com/tools/perfetto
- **[A9] Perfetto — FrameTimeline:** https://perfetto.dev/docs/data-sources/frametimeline
- ADB / dumpsys: https://developer.android.com/tools/adb ; https://developer.android.com/tools/dumpsys

### Código cliente e transporte

- **[C1] Moonlight renderer:** https://github.com/moonlight-stream/moonlight-android/blob/b48494cb96bff23d8886c4775cc4f39a1075495d/app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java
- **[C2] Moonlight helper:** https://github.com/moonlight-stream/moonlight-android/blob/b48494cb96bff23d8886c4775cc4f39a1075495d/app/src/main/java/com/limelight/binding/video/MediaCodecHelper.java
- **[C3] Artemis renderer:** https://github.com/ClassicOldSong/moonlight-android/blob/c5cf27f4dc822db0e863c4691e7a70c74bea977a/app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java
- **[C4] Artemis helper:** https://github.com/ClassicOldSong/moonlight-android/blob/c5cf27f4dc822db0e863c4691e7a70c74bea977a/app/src/main/java/com/limelight/binding/video/MediaCodecHelper.java
- **[C5] common — VideoDepacketizer:** https://github.com/moonlight-stream/moonlight-common-c/blob/94c65fd364d17b223f00a8763c9a5d94a66cb8dd/src/VideoDepacketizer.c
- **[C6] common — capacidades públicas / entrega:** https://github.com/moonlight-stream/moonlight-common-c/blob/94c65fd364d17b223f00a8763c9a5d94a66cb8dd/src/Limelight.h ; https://github.com/moonlight-stream/moonlight-common-c/blob/94c65fd364d17b223f00a8763c9a5d94a66cb8dd/src/VideoStream.c

**Estado da entrega:** documentação e coletor somente. Sintaxe PowerShell validada; execução Android, probe MediaCodecList, Perfetto e benchmarks pendentes. Nenhum default alterado; nenhum commit criado.
