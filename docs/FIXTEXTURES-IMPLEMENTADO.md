# FixTextures: implementação e validação

O plugin e o gerador usam o pacote Java `dev.fixtextures`, artefatos `FixTextures*.jar` e pasta `plugins/FixTextures`. O repositório e o site continuam em `Ny3san331/moderntextures` para preservar os endereços publicados. Documentos de planejamento descrevem o histórico da migração.

## Funcionalidades

- Texturas de blocos e itens existentes, entidades, equipamentos, partículas e trims disponíveis no cliente alvo.
- Aliases para recursos movidos na 26.3, incluindo armaduras antigas, equipamentos de animais e entidades reorganizadas.
- Validação de dimensões e relatório de texturas incompatíveis ou sem substituição.
- Modelo de lança com sprite e transformações de mão próprios; a animação de ataque continua dependente do item substituto.
- Manifestos schema 1 e 2, seleção de variantes por jogador e persistência em `players.yml`.
- `/ft texture list` e `/ft texture <nome>` para seleção pessoal; comandos administrativos protegidos por `fixtextures.admin` ou permissão antiga.
- Tutorial instalado automaticamente e migração dos arquivos da pasta antiga.

## Validação executada

- Testes Gradle de core, gerador e servidor HTTP.
- Teste de aliases de equipamento/entidades e rejeição de dimensões incompatíveis.
- Smoke Paper 26.3: inicialização, migração, tutorial, `/ft reload`, `/ft texture list`, HTTP GET/HEAD, Range (206) e arquivo ausente (404).

O smoke HTTP usou o bundle anterior para verificar a retrocompatibilidade. A validação visual de cada versão ainda requer clientes reais. Ter um perfil para cada protocolo não significa converter todos os layouts: camas, placas e entidades com UV alterado podem continuar com recursos locais. Novos blocos que compartilham o mesmo substituto Via não podem ser diferenciados apenas por resource pack.
