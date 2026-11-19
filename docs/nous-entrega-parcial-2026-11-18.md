# NOUS — entrega parcial e pendências — 18/11/2026

## Escopo adicional — conta NOUS e desktop

[Login, dispositivos e desktop](nous-conta-dispositivos-desktop.md) registra a janela própria no MSI/Inno `1.0.390.866` e o Google opcional no APK debug 60. No MuMu, o usuário concluiu o login Google; a entrada do PC foi preservada e ainda pede pareamento. Diálogos próprios Android usam o tema Material 3 compartilhado: conta, preferências, confirmações, progresso, menu de transmissão e editores. Telas Google/sistema inalteradas. APK debug 60 recompilado: 82 testes aprovados; reinstalação no MuMu manteve a sessão Google e a entrada do PC. Ainda faltam inspeção visual integral, texto ampliado, hardware e validação em sessão real. Windows/Web por conta, Discord, mescla, mesma conta sem PIN, QR local sem conta, convite entre contas e revogação por instalação continuam pendentes. Nenhuma validação de instalação real ou streaming foi feita nesta revisão.

## Revisão de interface e APK debug 61

- Browser streaming pausado na Web atual e clássica: sem atalhos nem tela de configuração; links antigos retornam ao painel. APIs e protocolos existentes não foram alterados.
- Desktop: fundo e moldura escuros, barra de status WebView2 desativada, navegação compacta revisada. Ajustes: cartões Tokens de API e Interface clássica espaçados. Falha na consulta automática de versões aparece só em Manutenção.
- Android: menu de sessão com largura mínima adaptada, rolagem e texto multilinha. APK debug 61 reinstalado no MuMu com `adb install -r`; manteve a data de primeira instalação. 82 testes Android aprovados. O painel sobre vídeo não foi validado em sessão real.
- 138 unitários Web e cinco cenários Playwright focados passaram; dois cenários da interface clássica foram ignorados sem URL da fixture. QR LAN e conexão por conta ainda não podem ser testados. Discord adiado a pedido do usuário.

## Revisão 1.0.0 — validação pendente

- Windows e Android exibem `1.0.0`; `FileVersion`, MSI `ProductVersion` e Android `versionCode` são técnicos. APK arm64 debug 59: 80 testes aprovados, zero falhas/erros. Atualizado no MuMu com `adb install -r`; manteve a data da primeira instalação. O cache agora considera `ArtVersion` sem mudar o ID da entrada. Ainda falta validar as quatro capas e a troca de capa personalizada de ponta a ponta com o host atualizado.
- O motor adia o início do host até verificar o ViGEmBus, sem reinstalar automaticamente um barramento compartilhado. Regressão simulada aprovada; instalação real e causa do bugcheck continuam não demonstradas. Usar Windows recuperável com snapshot.
- Teste MuMu: criar **tela virtual secundária** no host, usar `Estendido + isolado` e capturar só a saída virtual. Comparar monitor físico antes, durante e depois (ativo, primário, resolução, frequência e posição). Não iniciar no host atual: `dd_configuration_option = ensure_primary` e a captura tem possibilidade de fallback. Não precisa de outro monitor físico.
- HIDMaestro continua sem integração: piloto deve ser opt-in, sem substituir o ViGEmBus padrão, sem USB/IP na primeira etapa e sem novo certificado no PC principal. Comparar modos Bluetooth reportados do GameSir Cyclone 2 no Android com as personas apresentadas no Windows; verificar botões, eixos, gatilhos, vibração, reconexão e Steam/jogos separadamente.
- CI: [Windows `44356490840`](https://github.com/flugeru/NOUS/actions/runs/44356490840), [Windows `44351044736`](https://github.com/flugeru/NOUS/actions/runs/44351044736) e [Android `44280373747`](https://github.com/flugeru/NOUS/actions/runs/44280373747) terminaram com falha. Build local não substitui CI aprovada e assinatura autorizada.

## Revisão anterior — 1.0.390.818

- As quatro capas padrão foram editadas com Python para 640×360 e incluídas no host, que as fornece ao Web e ao Android. Testes Web focados: 7 aprovados, mais teste unitário de categoria. Steam Big Picture vem em Launchers nas instalações novas; catálogos antigos sem escolha explícita usam o comando de abertura como padrão.
- Android: 79 testes locais aprovados, incluindo cartões 16:9 e categorias. APK permanece o mesmo. Falta testar o recebimento das quatro capas pelo MuMu, em host de teste pareado, sem limpar dados. Remote Input e Remote Monitor têm IDs fixos; o APK atual guarda capa por ID e não usa `ArtVersion`. Entradas com cache anterior podem manter a arte antiga.
- Web instalado: conferidos Dispositivos, Estatísticas, Registros, Integrações, Manutenção e Transmitir no navegador (sem iniciar sessão). Remoção autorizada de uma entrada de pareamento de teste, confirmada no painel; a posição de tela salva não foi alterada.
- Análise local com depurador Microsoft assinado e servidor público de símbolos: bugcheck `0x50`, processo `System`, `ViGEmBus.sys` (`AV_ViGEmBus!unknown_function`). O dump parcial não estabeleceu a sequência que acionou a falha, nem a versão exata do binário envolvido. Não confundir correção no fluxo de instalação com correção do driver. Dump e saída bruta ficam locais.
- Setup/MSI recompilados, mas não executados. Validar instalação limpa, upgrade, preservação pré/pós, rollback, reinício e controle em Windows com snapshot antes do PC principal. O host acessa backends de controle na inicialização, mesmo sem streaming: não usar segunda instância no PC principal como substituto do ambiente recuperável.
- CI: [Windows anterior](https://github.com/flugeru/NOUS/actions/runs/44351044736) falhou em `Build Windows`; [Android anterior](https://github.com/flugeru/NOUS/actions/runs/44280373747) falhou na captura do emulador. [Windows da revisão](https://github.com/flugeru/NOUS/actions/runs/44356490840) ainda não certificado.

## Atualização pós-instalação — revisão 1.0.390.802 (histórico)

Esta seção substitui o estado e os hashes históricos abaixo. Consulte `LEIA-ME.md` e `SHA256SUMS.txt` para os binários atuais.

- Elevação confirmada. Host anterior `1.0.390.687` e serviço em execução; APIs de biblioteca, histórico e SteamGridDB verificadas. MSI anterior registrou sucesso, mas não houve comparação antes/depois de dados, nem teste de upgrade/rollback com o setup novo.
- Windows registrou tela azul `0x00000050`. Minidump local preservado, sem causa identificada. Testar primeiro em Windows descartável com snapshot, incluindo caso ViGEmBus ausente, reinício, reparo e upgrade. Nenhum driver, serviço ou certificado foi alterado nesta revisão.
- Setup `1.0.390.802` verifica o serviço ViGEmBus antes de decidir instalar a versão oficial embutida. Falha do componente aparece como aviso e fica no log, sem desfazer o host. Build local sem assinatura; MSI não inclui VHF NOUS. Regressões e compilação do Inno aprovadas; tela/progresso durante instalação real não verificados.
- Web: 138 unitários, build e typecheck aprovados. E2E Edge: 222 aprovados, 16 ignorados, 1 falha de abertura de contexto que passou isolada. Auditoria autenticada completa não concluída.
- MuMu confirmou abertura do APK arm64 debug 58; após remoção autorizada de duas entradas offline, resta uma pedindo pareamento. APK não mudou. POCO, controles físicos, toque e decodificação real continuam pendentes. Não iniciar streaming sem segundo monitor isolado.
- CI Windows/Android anterior falhou; download de logs recusado (403). Regressões locais aprovadas não substituem CI. Release depende de CI e assinatura autorizada.

## Estado da entrega anterior (histórico)

Esta rodada concluiu o que não exige UAC: integração e validação do proxy SteamGridDB, exportação de diagnósticos, sugestões de apps frequentes, modos de preferência por dispositivo, testes web, build do host e recompilação do MSI/setup Inno. **Não representa auditoria integrada concluída nem release final.** Instalação/upgrade/rollback reais, drivers, streaming e hardware físico continuam pendentes.

Branch: `feat/nous-ecosystem-audit`. Contrato completo e evidências anteriores: `docs/nous-ecosystem-audit.md`. Instruções da pasta de entrega: `docs/nous-entrega-total-2026-11-18.md`.

## Artefatos desta rodada

Pasta de artefatos relativa ao repositório: `build-local/ENTREGA-TOTAL-2026-11-18` (não versionada).

| Arquivo | Bytes | SHA-256 |
| --- | ---: | --- |
| `setup.exe` | 80144336 | `D2879A201FE35E0DAE88A7DA5172B77D748D0066F499F63127A32AF3DA7902C0` |
| `NOUS.msi` | 74492782 | `6AA258332E41EAEF857CAA4B5BA9FA7A055AA5AD60074D81B3C8A7A659838D69` |
| `nous-install-engine.exe` | 81124352 | `BA9BDB623C8837C382519C9687F771B44B5CE2C87CFE7A2FE289A21D83C44A38` |
| `NOUS-Android-arm64-debug.apk` | 63188991 | `9413D9E9BBDD90D6F8FCF6B6A53A87EDFCA882C2FF56C0CB7614372FD50D1264` |
| `NOUS.exe` (build interno) | 85041133 | `E3E36F11C43CF3FCED426A4EE88D0CDF1654E98C09CC8F85AB6331A8E05CA4B6` |

- Host/engine: FileVersion `1.0.390.687`. MSI ProductVersion `1.0.390.0`; o setup usa a versão do MSI para `AppVersion` e a FileVersion para `VersionInfoVersion`.
- Setup Inno Setup 6.7.4, fornecedor **Flűgel**, motor C# e MSI incorporado como `Payload.msi`. O empacotamento verifica o SHA-256 do MSI incorporado contra o MSI selecionado antes de compilar o Inno.
- `setup.exe` e `NOUS.msi` locais estão **NotSigned** (Authenticode ausente). Não desative proteções do Windows para executá-los.
- O APK não foi recompilado nesta rodada; permanece o build debug arm64 anterior (`com.flugel.nous`, versionCode 58).
- Artefatos ficam fora do Git; a fonte e a documentação estão na branch.

## Concluído e validado nesta rodada

### Capas horizontais SteamGridDB

- Proxy autenticado no backend: `GET /api/covers/steamgrid/search` e `POST /api/covers/steamgrid/assets`.
- O navegador não chama o SteamGridDB diretamente; o backend usa somente HTTPS, limita a resposta a 2 MiB, valida termo (1–100 caracteres), `game_id`, `asset_type` (`grid`/`hero`), página (0–50) e limite (1–30).
- Cache em memória com TTL de 15 minutos e limite de 128 entradas. O pool de rotas é separado do pool de lançamento/streaming para que uma falha do provedor não bloqueie `/api/apps/launch`.
- Respostas são sanitizadas: somente campos de artwork necessários, URLs apenas de `https://cdn2.steamgriddb.com/`, sem repassar payload bruto do provedor. Erros retornam 502 com mensagem genérica.
- Upload de capa passou a aceitar `images.igdb.com` e `cdn2.steamgriddb.com`, somente HTTPS; outros hosts retornam 400. O arquivo continua sendo convertido e gravado localmente.
- Apresentação 16:9 é feita por recorte (`object-fit: cover`) em frame 16:9. Grids observados são 920×430 e heroes 3840×1240; **não são arquivos 16:9 nativos** e a interface não os declara como tal.
- Fallback: GameDB (IGDB) continua disponível em paralelo; se ambos falharem, o modal mostra erro e o upload local continua possível.

Validação histórica em instância temporária isolada, com portas de teste distintas do host instalado:

- Busca `portal`: HTTP 200 com 10 jogos; resposta inclui `Portal` (id 9022).
- Assets grid: HTTP 200, 3 itens, 920×430, URLs oficiais do CDN.
- Segunda chamada idêntica: 0,389543 s → 0,005745 s (cache).
- Assets hero: HTTP 200, 3840×1240.
- Sem autenticação: HTTP 401. `asset_type` inválido: HTTP 400. `term` vazio: HTTP 400. `game_id` acima de `int64`: HTTP 400 sem derrubar o processo.
- Fluxo de navegador: `/api/csrf-token` → `/api/auth/login` → busca/assets/upload com cookie de sessão e `X-CSRF-Token`; todos HTTP 200.
- Upload real de capa SGDB: HTTP 200 e PNG de 71233 bytes gravado em `config/covers/`.
- Upload de `https://example.com/a.png`: HTTP 400.

### Diagnósticos e histórico

- Defaults de retenção: 30 dias e 512 MiB (`session_history_ttl_days`, `session_history_db_size_limit_mb`) no host e nas duas interfaces web.
- Exportação manual em JSON com allowlist explícita de campos de sessão. Credenciais, tokens, headers, IPs, payloads brutos e identidades de rede não entram.
- O export registra `excluded` (garantias fixas), `history.available`, `history.degraded`, `history.truncated` e a contagem real; ausência ou degradação não é mascarada.
- Paginação limitada a 5000 sessões por exportação; ao atingir o teto, o arquivo marca `truncated: true` e a interface avisa.
- A tela mostra alertas distintos para histórico indisponível e degradado, sem inventar sessões.

### Apps frequentes

- Ranking por sessões concluídas nos últimos 30 dias, com desempate por uso mais recente e nome.
- A seção “Frequentes nas suas sessões” apenas filtra aplicativos que já existem na biblioteca; nada é adicionado automaticamente.
- Falha ou ausência de histórico não exibe erro nem inventa sugestões.

### Preferências por dispositivo

- Seletor `synced` / `mixed` / `device` por cliente pareado.
- `synced`: remove overrides locais e segue o host.
- `device`: fixa os valores atuais de todas as opções compatíveis.
- `mixed`: estado com overrides parciais; o usuário edita as opções abaixo. As opções de transmissão/controle permanecem locais ao cliente e não são copiadas indiscriminadamente.

### Testes e build

- Web: **138 testes unitários, zero falhas** (`npm.cmd run test:unit`).
- Web build/typecheck: aprovado (`npm.cmd run build`).
- Host: `cmake --build build-local --target sunshine -j 8` aprovado; `NOUS.exe` 85041133 bytes.
- MSI: `cmake --build build-local --target package_msi -j 8` aprovado.
- Setup: `build_bootstrapper.ps1 -BuildDir build-local -MsiPath build-local\cpack_artifacts\NOUS.msi -OutputName setup.exe -DisableSignPath` aprovado.
- Instância isolada: `/api/apps` e `/api/history/sessions` responderam; `history_status.available=true`, `degraded=false`; `/api/auth/status` confirmou sessão autenticada.

## Não concluído

### Instalador, host e drivers (exigem UAC)

- Instalação, upgrade e rollback reais do `setup.exe`; preservação efetiva de configurações, credenciais, pareamentos e biblioteca.
- Verificação visual da janela Inno, progresso real durante MSI/drivers, mensagens de conclusão e comportamento em reinício.
- Drivers de tela virtual e controle virtual: confiança de certificado, instalação, desinstalação e recuperação.
- Teste do host recém-instalado em `47990` e dos fluxos que dependem de serviço/elevação.

### Android, sessão e hardware

- Transmissão real no MuMu usando somente segundo monitor isolado.
- Alça lateral durante sessão, recolhimento por inatividade, menu rápido e navegação por controle.
- Xbox/DualSense físicos, USB/Bluetooth, controles divididos e múltiplos controles.
- Performance, latência, estabilidade e Warp em sessão real. MuMu não comprova hardware POCO nem desempenho.

### Produto e auditoria

- Auditoria integral de todas as páginas Web e opções Android: backend real, persistência, permissões, acessibilidade, retrato/paisagem e estados vazios/erros.
- Revisão visual completa preservando fundo escuro, ações rosa e navegação ciano.
- GitHub Actions não executado/verificado nesta entrega; `gh` não está instalado neste ambiente.
- Assinatura Authenticode do setup/MSI e empacotamento de release final.
- O browser MCP permanece desconectado; não repetir chamadas até reconexão/decisão do usuário.

## Cuidados para retomada

1. Preserve a instalação histórica: registro `HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Vibepollo` foi observado com `InstallLocation=C:\Windows\` e `DisplayVersion=1.0.3-local`. Configurações/credenciais históricas em `C:\Windows\config`. Faça backup protegido antes de instalação real; não exponha conteúdo em logs/documentos públicos.
2. Não apague chaves históricas `SOFTWARE\Nonary\Vibepollo\Installer\DriverSelections` nem a identidade ARP `Vibepollo` por causa da mudança de fornecedor.
3. Não use PIDs ou caminhos de uma sessão anterior para selecionar processos; confirme a identidade da instância de teste antes de qualquer ação.
4. Retome o contrato em `docs/nous-ecosystem-audit.md`. Respostas privadas e inventários locais não integram a documentação pública. Preserve alterações existentes.

## Comandos já utilizados

```powershell
# Testes e build web
cd src_assets\common\assets\web
npm.cmd run test:unit
npm.cmd run build

# Host, MSI e setup
cmake --build build-local --target sunshine -j 8
cmake --build build-local --target package_msi -j 8
$env:INNO_SETUP_COMPILER=(Get-Command ISCC.exe).Source
powershell -NoProfile -ExecutionPolicy Bypass -File packaging\windows\bootstrapper\build_bootstrapper.ps1 -BuildDir build-local -MsiPath build-local\cpack_artifacts\NOUS.msi -OutputName setup.exe -DisableSignPath
```

Scripts temporários e ferramentas locais podem precisar reinstalação em outro computador. O workflow Inno foi configurado para 6.7.4, mas ainda precisa validação real em CI.
