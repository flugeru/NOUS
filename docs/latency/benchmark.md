# Protocolo de benchmark: Windows 11 → Poco X6 Pro

## O que existe e o que não existe

`tools/latency/analyze_trace.py` analisa CSV por frame com biblioteca padrão Python 3. Não captura frames, não instrumenta automaticamente Vibepollo/Moonlight e não mede input-to-photon. Alimentação exige exportador de telemetria por frame ou conversão explícita de traces. Não converter médias agregadas do overlay em timestamps inventados.

- Host: `capture_ms`, `encode_end_ms`, `send_ms`, todos no mesmo relógio monotônico do host.
- Cliente: `receive_ms`, `decode_end_ms`, `display_ms`, todos no mesmo relógio monotônico Android.
- `frame_id`: ID não negativo do frame de vídeo, preservado entre etapas. Não confundir com RTP sequence number (pacote), índice de buffer MediaCodec ou contador de callbacks.
- Campos ausentes ficam vazios; pelo menos um timestamp por linha. Cada sessão em arquivo separado; IDs e tempos não podem regredir dentro de etapa. Linha representa evento do mesmo frame nas etapas preenchidas. Repetição de apresentação pode repetir ID em outra linha com apenas `display_ms` preenchido.
- `capture_ms`: defina se origem de apresentação ou chegada à captura. Não misturar definições entre builds. `encode_end_ms`: saída disponível; `send_ms`: término de envio do frame; `receive_ms`: frame completo após FEC; `decode_end_ms`: saída do decoder. `display_ms` precisa indicar apresentação observada, não mera chamada de `releaseOutputBuffer`. Callbacks podem ter atraso e não são medição de fótons.

Separação de relógios é deliberada: não subtrair envio Windows de recepção Android sem sincronização e erro de offset documentados. RTT/2 não comprova atraso unidirecional. Filas de referência não podem descartar arbitrariamente frames codificados: descarte requer política de dependências/recovery. Captura anterior ao encode pode preferir frame recente quando contrato permite.

## Execução

```powershell
python tools/latency/test_analyze_trace.py
python tools/latency/analyze_trace.py before.csv --fps 120 --output before.json
python tools/latency/analyze_trace.py after.csv --fps 120 --baseline before.json --output after.json
```

Arquivos de saída existentes são recusados para preservar resultados. Exemplo **sintético**, não medição real:

```csv
frame_id,capture_ms,encode_end_ms,send_ms,receive_ms,decode_end_ms,display_ms
0,1000,1002,1003,10,11,12
1,1010,1012,1013,20,21,22
2,1020,1022,1023,30,31,32
```

Resultado: distribuições min/média/p50/p95/p99/máximo/desvio populacional; intervalos por etapa; erro absoluto contra período alvo; intervalos >1,5× período; IDs repetidos/ausentes; FPS observado. Percentis usam interpolação linear de rank `(N−1)p`. Delta é `depois−antes`, não teste estatístico. IDs ausentes significam não observados nessa etapa, não necessariamente perda de rede; repetição de ID não comprova pixel duplicado. Campos `*_ms` são milissegundos decimais. Exportação JSON usa ponto decimal; CSV não aceita vírgula decimal sem conversão.

## Ensaio A/B reproduzível

1. Registre commit, build, driver, Windows/HyperOS/Android, versão do cliente, decoder selecionado, codec/perfil/bit depth, resolução, FPS, bitrate/FEC, captura, presets, multipass, HDR, atualização real do painel e topologia de rede. Salve configuração saneada sem chaves, tokens ou dados de pareamento.
2. Use PC por Ethernet e telefone próximo ao AP em banda não congestionada. Registre Wi-Fi, RSSI, frequência/largura quando disponíveis, temperatura, estado de carga, economia de energia e bateria. Não concluir capacidade por velocidade nominal do link.
3. Comece sem frame generation, uma sessão, sem gravação paralela. Fixe cena/replay e limite de FPS estável suportado pelo jogo; mantenha folga GPU. Não aplicar desativação global de V-Sync/HAGS/MPO/segurança. Meça cada opção isoladamente quando necessário.
4. Faça aquecimento de 2 minutos e colete pelo menos 3 minutos de movimento contínuo; depois repita 15 minutos para throttling. Desktop parado não serve para contar drops: host pode repetir quadro por design. Escolha duração maior para eventos raros.
5. Rode pelo menos cinco pares alternados A/B e B/A. Mesmo local, cena, resolução e bitrate; mude só uma variável. Reporte distribuição por execução, não apenas média de médias. Não compare deltas de traces com durações/cenas/temperaturas incompatíveis.
6. Compare 60 FPS e 120 FPS em painel efetivamente 120 Hz; 90 FPS em 120 Hz produz cadência desigual sem mudança de refresh. 120 Hz não prova decoder 120 FPS na resolução nativa. Teste 1920×1080 antes de 2712×1220 em paisagem; preservar proporção ou documentar barras/crop.
7. Compare HEVC Main/Main10 e AV1 Main 8/10-bit **somente quando hardware decoder exposto e estável**. AV1 é candidato por eficiência, não vencedor automático. Avalie qualidade com sequência/crop comparáveis e inspeção visual; VMAF/SSIM opcionais offline, sem usar captura comprimida como referência perfeita. Não reduzir qualidade para ganhar benchmark.
8. Inspecione p99 de encode, receive, decode e apresentação, excursões de fila, bursts FEC/IDR, frames não observados e glitches de áudio. Aceite alteração somente sem regressão de estabilidade/qualidade, inclusive recovery de Wi-Fi e reconexão.

## Diagnóstico por estágio

| Sinal | Hipótese a investigar | Evidência necessária |
|---|---|---|
| Captura irregular, rede estável | jogo/DWM/WGC/DXGI/overload | PresentMon/ETW, timestamps de captura, GPU load |
| Encode p99 cresce sob jogo | falta de folga GPU, cópias, AQ/multipass | telemetria NVENC e GPU por frame; A/B com mesmo codec |
| Send irregular, encode estável | pacing, scheduler, congestionamento | timestamps de saída, fila socket, captura de pacotes |
| Receive irregular, send estável | Wi-Fi, bursts, perda/FEC | timestamps em ambos hosts, stats rede; não misturar clocks |
| Decode lento só após aquecimento | throttle/firmware/perfil/resolução | MediaCodec/Perfetto/thermal e decoder efetivo |
| Decode estável, display irregular | vsync/refresh/SurfaceFlinger/filas | FrameTimeline, callbacks e apresentação; não FPS médio |
| FPS correto e latência crescente | fila acumulada | idade por frame, depth por estágio |

Use ferramentas oficiais somente quando disponíveis: PresentMon para apresentação do jogo (não apresentação no telefone), Windows Performance Recorder/Analyzer e GPUView para scheduling/cópias, Perfetto/FrameTimeline no Android para composição. Verifique suporte e permissões; tracing tem overhead, compare também execução sem tracing. Não instalar drivers, alterar temporizadores globais ou elevar prioridade para realtime por suposição.

## Rede: latência de cauda antes de throughput máximo

[FQ-CoDel, RFC 8290](https://www.rfc-editor.org/rfc/rfc8290), especificação experimental, documenta isolamento entre fluxos e controle de filas persistentes para combater bufferbloat; §5.6 destaca filas escondidas no driver/hardware. É fundamento para investigar AQM/SQM no gargalo real, não instrução para copiar defaults de roteador nem prova de suporte no AP atual. Shaping de WAN não resolve automaticamente congestionamento de airtime na LAN Wi-Fi; filas podem ficar depois do ponto controlado.

- Compare RTT em repouso e sob upload/download concorrente **em ensaio separado**, com consentimento e sem saturar rede de terceiros. RTT baixo em repouso não prova jitter baixo durante vídeo.
- Bitrate do encoder não inclui necessariamente FEC, RTP/UDP/IP, criptografia e camada Wi-Fi. Planeje orçamento por bytes efetivos e tempo no ar; IDR e AQ temporal podem criar bursts mesmo com CBR médio.
- FEC pode reparar perda sem esperar retransmissão, mas aumenta bytes/CPU e pode agravar congestionamento. Não remover para ganhar benchmark limpo; compare recuperação, taxa de perdas residuais e idade do frame sob falhas controladas.
- Pacote maior reduz overhead, mas pode fragmentar em MTU/túnel e piorar recuperação; pacote menor aumenta taxa de pacotes e overhead. Não impor jumbo frames, DSCP ou tamanho único sem validar caminho e cliente.
- Pacing distribui envios, mas espera imprecisa do scheduler pode agrupar pacotes. Instrumente `due` versus envio efetivo e fila de socket antes de substituir sleeps por busy-wait ou elevar prioridade.
- UDP evita bloqueio de entrega ordenada do TCP no transporte de vídeo, mas não elimina filas de aplicação/kernel/AP, perda ou necessidade de recovery. Não desativar criptografia: custo precisa ser medido, segurança preservada.

Nenhuma alteração no roteador, NIC, firewall, FEC ou Wi-Fi foi aplicada nesta entrega.

## Input-to-photon

Câmera de alta velocidade com ação e alteração visual no mesmo enquadramento, ou instrumentação física equivalente. Calibre exposição, rolling shutter, posição de varredura e intervalo de amostragem. Colete muitos acionamentos com fases diferentes do refresh. Reporte p50/p95/p99 e incerteza; 240 FPS amostra a cada 4,17 ms. Overlay de decode, ping e soma de médias não substituem essa medição.

## Critério de promoção

Sem limiar universal inventado. A 120 FPS orçamento de período é 8,333 ms; a 60 FPS, 16,667 ms. Encode+decode abaixo desse valor não garante input-to-photon abaixo de um período. Alteração deve reduzir cauda ou incidentes repetidamente, sem regressão visual/recovery, com intervalo de confiança ou variação entre pares documentado. Nenhum ganho no hardware é declarado nesta primeira auditoria.
