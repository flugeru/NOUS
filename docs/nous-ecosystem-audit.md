# Auditoria integrada NOUS

## Contrato aprovado em 17/11/2026

Entrega única integrada: host Windows com interface Web, backend, Android e setup.exe Inno Setup 6 com MSI incorporado. Não criar interface Desktop nativa separada. Preservar protocolos existentes e dados do usuário.

- Branch: `feat/nous-ecosystem-audit`; commits locais e remotos devem permanecer sincronizados.
- Biblioteca em ambas as interfaces. Categorias Desktop (área de trabalho e programas escolhidos), Jogos e Launchers. Usuário decide o que adicionar; detecção apenas sugere.
- Identidade atual preservada: padronizar botões, estados, ícones e cores antigas remanescentes. Credenciais dentro de Ajustes, separadas de Integrações.
- Capas horizontais 16:9: pesquisa pública SteamGridDB sem login/chave; respeitar bloqueios, cache e alternativa local. Nunca contornar CAPTCHA.
- Preferências locais por padrão, com modos sincronizado e misto. Metadados de aplicativos pertencem ao host; configurações de transmissão e controles não são copiadas entre dispositivos indiscriminadamente.
- Controle virtual dentro de Controles. Demonstração exclusivamente em Desenvolvimento.
- Menu rápido com atalho configurável por controle, incluindo Xbox/DualSense; comando reconhecido reservado ao menu. Alça lateral recolhe após inatividade, preservando acesso.
- Diagnósticos reais de host/cliente, histórico local de 30 dias com limite de tamanho e exclusão manual. Sem gravação de tela, áudio, teclas ou área de transferência; exportação manual sem credenciais.
- MuMu: testes isolados usando segundo monitor, sem desligar/alterar monitor principal. Usuário autorizou instalação de APK, reinícios e testes de instalação/atualização preservando dados e configurações.
- Progresso do instalador deve refletir operações reais, não temporizador fictício.

## Entrega total solicitada em 18/11/2026

Usuário reduziu o escopo imediato para binários compilados e pendências registradas, depois pediu a pasta de entrega total. Pasta local: `build-local/ENTREGA-TOTAL-2026-11-18`; pacote `build-local/NOUS-ENTREGA-TOTAL-2026-11-18.zip` (hash externo em `NOUS-ENTREGA-TOTAL-2026-11-18.zip.sha256`; hashes dos arquivos internos em `SHA256SUMS.txt`). Contém `setup.exe`, `NOUS.msi`, motor, APK arm64 debug, auditoria, pendências, versão/hashes e LEIA-ME. Instruções: [`nous-entrega-total-2026-11-18.md`](nous-entrega-total-2026-11-18.md). Pendências detalhadas: [`nous-entrega-parcial-2026-11-18.md`](nous-entrega-parcial-2026-11-18.md). **Auditoria integral não concluída; não é release final.**

## Revisão pós-instalação — build 1.0.390.802

- O serviço instalado `ApolloService` executa `C:\Windows\NOUS.exe`, versão numérica `1.0.390.687`, embora o texto legado mostre `1.0.3-local`. A interface em 47990 respondeu HTTP 200; biblioteca categorizada, histórico e busca SteamGridDB responderam com autenticação. O log anterior registra MSI concluído com código 0; isso não comprova preservação por comparação antes/depois.
- O Windows registrou bugcheck `0x00000050` às 07:01:40, após o MSI anterior. Minidump preservado localmente; módulo causador ainda não identificado. Nenhum driver foi alterado nesta revisão. O ViGEmBus instalado manualmente está ativo: instalador 1.22.0, versão do driver `1.21.442.0`. O MSI local não contém VHF NOUS.
- O setup revisado incorpora ViGEmBus oficial assinado e verifica o SHA-256 do payload. Consulta estado do serviço pelo SCM, não só presença do arquivo; preserva driver igual em execução e nunca faz downgrade de versão conhecida. Falhas depois do MSI aparecem como aviso, sem rollback do host registrado; o resultado entra no log persistido. Inno não concede confiança a certificado beta em modo silencioso e exige consentimento vinculado ao certificado em modo interativo.
- Windows: compilações host, MSI e Inno 6.7.4 aprovadas; regressões locais de instalação, consentimento, segurança, rollback (387 checks) e interop aprovadas em fixtures, sem executar instalação/driver. Compilação do ramo Inno VHF com consentimento também aprovada usando placeholder, sem instalação.
- Web: build/typecheck e 138 unitários aprovados. Playwright/Edge: 222 aprovados, 16 ignorados e 1 falha de abertura de contexto na suíte completa; o caso restante passou isolado. Regressões cobrem interface com fixtures, não streaming nem hardware real.
- MuMu: APK arm64 debug 58 abriu em execução real; duas entradas offline foram removidas com autorização pela interface Android, restando uma entrada que pede pareamento. Sem limpeza de dados ou streaming. Apenas um monitor detectado. POCO, controles físicos e segundo monitor continuam pendentes.
- CI anterior: Windows falhou no passo de segurança/rollback e Android no de capturas; logs dos jobs retornaram HTTP 403 sem autenticação. A regressão local de texto de consentimento desatualizado foi corrigida; não comprova a causa da falha remota. Nova CI e assinatura de release não estão certificadas.

## Evidências iniciais (não equivalem a auditoria concluída)

- `AppGridAdapter.java`: categorização limitada a `demoCategories`; aplicativos reais ignoram filtro de categoria.
- `NvApp.java` e `/applist` em `src/nvhttp.cpp`: sem metadados de categoria.
- `LibraryView.vue`: pesquisa e provedores, sem categoria/execução no trecho de filtragem inspecionado.
- `NousSettingsTab.java`: editor virtual fora de Controles.
- `DebugInfoActivity.java`: diagnóstico de dispositivo/controle, não sessão.
- `GameMenu.java`: identificação de ações por textos traduzidos.
- Host local nas portas 47989/47990/48010; navegador conectado, certificado local inicialmente recusado.
- ADB inicial encontrou `emulator-5554` offline. MuMu requer identificação e conexão.

## Preservação

Branch criada sobre `c31114155`. Alterações preexistentes de Inno/WiX e `build-local/` preservadas; não sobrescrever nem adicionar artefatos de build ao Git. Compilação anterior instalada pelo usuário não comprova validação das alterações desta branch.

## Evidência de validação

Registrar comandos, resultados e limitações durante execução. Não tratar inspeção estática, mocks ou build como prova de comportamento real.

### 17/11/2026 — biblioteca e navegação

- Web: `npm run build` passou; `npm run test:unit` passou com 126 testes. Categoria manual prevalece sobre categoria efetiva; valores desconhecidos mantêm fallback compatível.
- Android: `build-android.ps1` passou após corrigir fixture do teste para não carregar JNI. Suíte com 76 testes, incluindo filtro combinado de categoria, pesquisa e execução em aplicativos reais do adapter, sem usar catálogo demonstrativo.
- MuMu `127.0.0.1:16384`: APK arm64 atualizado com `adb install -r`, sem limpar dados; pacote `com.flugel.nous` abriu. Ajustes, seção Controles e editor Controle virtual abriram por interação real. Editor expôs Salvar, Desfazer, Refazer, modos de edição e descrições acessíveis dos botões; retorno sem salvar não gerou erro AndroidRuntime. PCs preservados apareciam verificando conexão; transmissão, alça durante sessão e hardware físico ainda não validados.
- Web real, antes do build: categoria Steam Big Picture salva como Launchers; recarregamento e filtro mostraram exatamente um aplicativo. Host instalado ainda não contém novos metadados; execução e categoria efetiva precisam validação com host recompilado.
- Host: validação de `library-category` restringe valores a `desktop`, `games`, `launchers`; campos de resposta `running` e `effective-library-category` removidos ao salvar. Build C++ atualizado aprovado em `1.0.390.101`. Em 18/11, os 11 casos do harness passaram, incluindo UUIDs nulos/não-string originais, categorias inválidas e metadados. Corrigida leitura de UUID na lista de apps restauráveis que convertia valores não-string e causava HTTP 400. Dados inválidos do teste preservados, sem substituí-los por UUID válido. Isso não equivale a validação runtime do host instalado.
- 18/11: Inno 6.7.4 compilou com etapas do motor e ações reais do log MSI na mesma janela, barra indeterminada sem percentual inventado e conclusão apenas após resultado verificado. Logs MSI usam flush; saída exibe somente nomes de ações, nunca propriedades. Regressões: 25 migração/metadados, 24 despacho de desinstalação, 7 diretório, 387 rollback; limites externos simulados, nenhuma instalação feita nesses testes. Empacotamento verifica hash do MSI incorporado antes de compilar; CI passa a instalar compilador Inno 6 fixado. Validação visual e instalação/upgrade reais ainda pendentes.
- 18/11: atalho do menu rápido configurável em Controles: legado, Guide/PS/Xbox, Start/Options/Menu, Select/Create/View, L3, R3 e desativado. Botão reservado é removido do pacote final, inclusive USB/controles divididos; combinações legadas não transformam botão reservado. 79 testes Android passaram; três testes novos cobrem cinco botões, repetição/release, desativação, preferência inválida e entrada USB simulada. Serviço SHIELD ausente simulado no teste; JNI usa shadows existentes. Hardware físico e transmissão ainda não validados.
- MuMu recebeu APK atualizado com `adb install -r`, mantendo PCs e seleção R3 entre reinstalação e reabertura de Controles. Aviso de reserva/interceptação Android aparece abaixo da opção. APK debug arm64 SHA-256: `9413D9E9BBDD90D6F8FCF6B6A53A87EDFCA882C2FF56C0CB7614372FD50D1264`.
- SteamGridDB público foi integrado pelo backend e validado em execução real. O navegador nunca chama o provedor diretamente; o proxy autenticado usa HTTPS, limites de termo/página/limite, 2 MiB por resposta, cache de 15 minutos/128 entradas, allowlist do CDN e pool separado do lançamento. Upload de capa aceita `images.igdb.com` e `cdn2.steamgriddb.com` via HTTPS. Grids 920×430 e heroes 3840×1240 são recortados em frame 16:9; não são declarados como arquivos 16:9 nativos. Evidência: busca/grid/hero HTTP 200, cache 0,389543 s → 0,005745 s, upload real de PNG (71233 bytes), 401 sem autenticação e 400 para host/entrada inválidos.
- Diagnósticos de sessão, exportação com allowlist, sugestões de apps frequentes e modos de preferência por dispositivo foram integrados. Auditoria integral de páginas/opções continua pendente. Nenhuma entrega final declarada.
