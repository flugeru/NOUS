# W1 — detecção de `RTCRtpReceiver.jitterBufferTarget`

Verificado em 2026-11-14. Correção restrita aos helpers modernos e legacy; `architecture-audit.md` não alterado.

## Evidência e correção

Especificação atual W3C WebRTC declara `attribute DOMHighResTimeStamp? jitterBufferTarget` e inicializa slot `[[JitterBufferTarget]]` com **`null`**. Portanto, `typeof receiver.jitterBufferTarget === 'number'` pode rejeitar API presente antes da primeira atribuição.

Três guards agora verificam `'jitterBufferTarget' in receiver`: `applyReceiverLatencyHints()` moderno e `applyAudioReceiverHints()` / `applyVideoReceiverHints()` legacy. `in` cobre propriedade herdada e não lê getter. Tipo auxiliar moderno aceita `number | null`. Permanecem `try/catch` dos setters, validação dos targets, unidades, valores e defaults existentes; API ausente não recebe propriedade artificial.

Fontes consultadas:

- **W3C, fonte normativa atual:** https://w3c.github.io/webrtc-pc/#rtcrtpreceiver-jitterbuffertarget — atributo nullable e comportamento do target.
- **W3C, inicialização:** https://w3c.github.io/webrtc-pc/#internalslot-jitterbuffertarget — slot inicialmente `null`.
- **MDN via Context7**, biblioteca `/mdn/content`, consulta sobre `RTCRtpReceiver.jitterBufferTarget`: https://developer.mozilla.org/en-US/docs/Web/API/RTCRtpReceiver/jitterBufferTarget — preferência em milissegundos e exceções do setter. Trecho retornado pelo Context7 não documentava `null`; nulabilidade confirmada diretamente na especificação W3C.

## Teste e resultado

Teste `src_assets/common/assets/web/scripts/webrtc-hints.test.ts` usa `node:test`, `node:assert/strict`, `stripTypeScriptTypes()` e `node:vm`. Extrai e executa funções **dos arquivos de produção**, sem duplicar lógica, alterar exports ou carregar imports exclusivos do navegador. Sem dependências novas.

No diretório `src_assets/common/assets/web`:

```powershell
node --experimental-strip-types --test scripts/webrtc-hints.test.ts
```

**Resultado: 14 testes passaram, zero falhas, Node v26.13.0.** Cobertura dos três helpers: valor inicial `null`, número, target zero, API ausente, receiver ausente, propriedade herdada com getter que lança e setter que lança. Legacy também preserva ausência de target. Node emite aviso experimental para `stripTypeScriptTypes()`.

Teste prova atribuição/tolerância a exceções em objetos simulados; não mede latência real nem suporte do navegador. Hints continuam preferências sujeitas à implementação. Sem alegação de ganho, sem alteração de negociação/default de codecs e sem inferência de suporte HEVC/AV1 no browser. Nenhum commit.
