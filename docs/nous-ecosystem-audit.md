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
