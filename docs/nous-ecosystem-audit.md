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

Usuário reduziu o escopo imediato para binários compilados e pendências registradas, depois pediu a pasta de entrega total. Pasta local: `build-local/ENTREGA-TOTAL-2026-11-18`; pacote `build-local/NOUS-ENTREGA-TOTAL-2026-11-18.zip` (SHA-256 `DCA33656EFBBD95EF76398E95408FE84876CA01CEF55A4851B9DF5228ADDE670`). Contém `setup.exe`, `NOUS.msi`, motor, APK arm64 debug, auditoria, pendências, versão/hashes e LEIA-ME. Instruções: [`nous-entrega-total-2026-11-18.md`](nous-entrega-total-2026-11-18.md). Pendências detalhadas: [`nous-entrega-parcial-2026-11-18.md`](nous-entrega-parcial-2026-11-18.md). **Auditoria integral não concluída; não é release final.**

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
- Pesquisa HTTP pública SteamGridDB respondeu sem login/chave. Autocomplete usa `term` duplamente codificado; pesquisa de assets usa POST com `game_id`, `asset_type`, filtros, página e limite. Grids encontrados em 920×430 e heroes em 3840×1240; nenhum equivale a 16:9 nativo. Integração/conversão ainda pendente; endpoint público não oferece CORS ao host Web.
- Diagnósticos de sessão integrados, sincronização de preferências, capas públicas e auditoria integral continuam pendentes. Nenhuma entrega final declarada.
