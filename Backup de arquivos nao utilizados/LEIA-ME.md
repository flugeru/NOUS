# Backup de arquivos nao utilizados

Pasta de quarentena e auditoria. Arquivos movidos estao listados em `arquivos-movidos.json`, com caminho original, motivo e hash. Nome antigo ou falta de referencia textual isolada nao prova que arquivo deixou de ser usado.

- Relatorios locais `inventario-de-diretorios.txt` e `auditoria-de-nomes.json` ficam fora do commit: incluem caminhos pessoais e referencias de arquivos gerados. Backup das fontes e manifesto de hashes permanecem versionados.
- Android e Linux sao somente inventariados, nao alterados.
- Dependencias upstream, licencas, creditos, protocolos, drivers assinados e deteccao de instalacoes antigas exigem preservacao ou migracao coordenada.

Distribuicao Windows pretendida: somente `setup.exe` como instalador publico. MSI continua necessario como carga interna e arquivo intermediario de compilacao. `uninstall.exe` continua sendo componente instalado para desinstalacao, nao outro instalador publico.

Auditoria inicial, nao conclusao de limpeza. Revisar candidatos antes de mover e registrar caminho de origem e motivo de cada movimentacao.
