# Auditoria de engasgos — Honkai: Star Rail / POCO X6 Pro

Fonte: `vibepollo-session-Honkai_Star_Rail-2026-11-17T12-13-30.json`, 567 amostras, quatro sessões RTSP, 2712 × 1220, HEVC SDR, alvo 60 FPS.

## Resultado por seção

- **Configuração:** JSON declara 60 FPS; imagem do histórico mostra 120 FPS. Não são equivalentes. Conferir registros individuais antes de atribuir 120 FPS ao arquivo. Agrupamento usa metadados de um membro e não representa mudanças de configuração de todos os membros.
- **Sessões:** médias 47,65 / 58,39 / 54,83 / 51,50 FPS. Cada sessão começa com uma amostra de FPS zero porque agregador precisa de duas leituras para calcular deltas. Zero inicial não mede uma janela real. Médias simples penalizam sessões curtas.
- **Duração/eventos:** 1134,6 s acumulados; oito eventos de início/fim, sem motivo de encerramento. Intervalos entre sessões não comprovam queda de conexão.
- **Bitrate:** 200000 kbps solicitados; 158988 kbps no encoder. Cálculo reproduzido em `src/rtsp.cpp`: 200000 × 0,8 − 512 − 500. Reserva de FEC, áudio estéreo e controle; diferença não é bug de unidades. Bitrate real inferior ao alvo não comprova defeito. Usuário localizou origem dos 200 Mbps na personalização de perfil; causa de valor inesperado continua sem reprodução. Teste de editor com slider de 20 Mbps grava 20000 kbps corretamente.
- **NVENC/GPU:** baixa latência de encode não mede captura, transporte, fila Android nem apresentação. Uso global e ocupação de encoder não excluem pausas transitórias. Pico de VRAM não comprova falha de alocação.
- **CPU/RAM/temperaturas:** `-1` significa indisponível. Web V2 já mostra temperatura indisponível como texto, não −1 °C. JSON mantém sentinela por compatibilidade.
- **FPS/jitter:** `frame_interval_jitter_ms` é desvio padrão cumulativo de intervalos médios calculados em janelas de aproximadamente dois segundos; não é jitter por frame. Primeiras janelas lentas continuam afetando valor. Não derivar P95/P99 de frametime real a partir desse campo.
- **Descartes/perdas:** amostras RTSP não preenchem `video_dropped`/`audio_dropped`; zeros são defaults, não medição Android. `client_reported_losses` depende do protocolo e não inclui todo descarte de apresentação. Histórico sem falha de escrita comprova integridade do registro, não saúde do vídeo.
- **Encode zero:** 25 amostras sem valor positivo. Campo não contém validade por amostra; não assumir codificação instantânea.
- **Saúde:** classificação antiga só verifica encode e perdas. Correção adiciona entrega lenta sustentada após cinco segundos de inicialização. Ainda é diagnóstico do host, não certificado de fluidez na tela.
- **Rede na imagem:** zero fora de transmissão ativa não contradiz tráfego registrado anteriormente. Métrica é utilização da interface do host, não bitrate de vídeo isolado.

## Bugs corrigidos nesta auditoria

1. Watchdog Android usava relógio nunca atualizado no recebimento de frames. Podia executar `flush()` após 1,2 s mesmo com decoder funcionando, sem coordenação e sem garantir quadro-chave. Removido; recuperação existente com threads quiescentes preservada.
2. Caminho alternativo drenava/renderizava buffers antes da lógica normal, ignorando pacing selecionado, contadores e recuperação. Removido.
3. Modo de suavidade descartava último frame disponível apenas por idade. Se decoder sempre ultrapassasse limiar, apresentação podia ficar sem atualização. Agora apresenta frame mais recente; descarte de antigos na fila preservado.
4. Mapa de tempos de decode era acessado por threads diferentes sem sincronização e publicado depois de `queueInputBuffer()`. Sincronizado, publicado antes do enqueue e limpo na recuperação/consumo.
5. `SeekBarPreference.getProgress()` somava mínimo a valor absoluto. Corrigido; não explica sozinho salto de 20 para 200 Mbps.
6. Campo manual de bitrate aceitava parse sem tratar erro e valores não finitos. Agora valida 0,5–300 Mbps e aceita vírgula decimal.
7. Exportação adiciona escopo host-only, definição de jitter e disponibilidade de descartes/métricas Android. Não inventa medições ausentes.

## Repositório original

Consultadas 38 branches de `Nonary/Vibepollo` em 2026-11-17. Comparados históricos exclusivos e variantes de filas, WGC, NVENC, RTSP e histórico. Correções relevantes encontradas: `2bed07660` (poll não bloqueante), `b195fb28e` (crédito de callback WGC, posteriormente revertido), `4aa4bb3d6` / `450141786` (timestamps de apresentação). Base NOUS já contém versões/equivalentes e alterações posteriores; não aplicar cherry-pick cego. Original não inclui árvore Android local. Nenhuma correção upstream pronta identificada para watchdog Android ou veredito de histórico.

## Limites

Correções de código não comprovam causa exclusiva do engasgo observado. Exige teste no POCO com APK novo e comparação antes/depois, mesmo perfil/jogo/rede. Instrumentação por frame de ponta a ponta, P95/P99 de apresentação, motivo de encerramento e transporte de métricas Android ao host permanecem pendentes; não foram fabricados a partir de amostras de dois segundos.
