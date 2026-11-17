# Correções pendentes

- [x] **Pareamento V2 abre tela vazia.** Corrigido bloqueio circular entre `await router.isReady()` no módulo principal e chunks de rotas lazy que importam esse módulo. Teste de produção passou com acesso direto, recarga, nome `2311DRK48G` preenchido/editável e envio somente após ação explícita. Abertura automática pelo Android no host instalado ainda exige validação real.
- [x] **Instalador: reformulação visual.** Nova composição, opções técnicas e ações secundárias recolhidas, ação principal ciano, textos principais em PT-BR e foco de teclado visível. Prévia renderizada revisada; 32 casos de layout passaram. Empacotamento em andamento.
- [ ] **Perfil Android: origem inesperada de 200 Mbps.** Ainda sem reprodução; selecionar 20 Mbps gravou corretamente 20000 kbps.
- [ ] **Engasgos no POCO.** Validar APK corrigido no aparelho com mesmo jogo, perfil e rede; testes locais não comprovam fluidez real.
