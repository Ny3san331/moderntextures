# Migração ModernTextures → FixTextures

Objetivo: renomear a marca sem quebrar servidores, URLs, manifests e packs já publicados.

## 1. O que muda

| Item | Antes | Depois |
|---|---|---|
| Nome do plugin | `ModernTextures` | `FixTextures` |
| JAR | `ModernTextures.jar` | `FixTextures.jar` |
| Pasta de dados | `plugins/ModernTextures/` | `plugins/FixTextures/` |
| Comando | `/mt` | `/ft` (com `/mt` como alias temporário) |
| Permissão | `moderntextures.admin` | `fixtextures.admin` (aceita a antiga) |
| Pasta de packs | `plugins/ModernTextures/packs/` | `plugins/FixTextures/packs/` |
| Manifesto | `manifest.json` | mesmo nome e formato (schema 1/2) |
| Artefato gerador | `ModernTextures-Generator.jar` | `FixTextures-Generator.jar` |
| Prefixo dos ZIPs | `moderntextures-26.3-to-…` | **mantido** |
| URL pública | `https://ny3san331.github.io/moderntextures/` | **mantida** |
| Repositório | `Ny3san331/moderntextures` | **mantido** |
| Pacote Java | `dev.moderntextures.*` | **mantido na fase 1** |

## 2. O que NÃO muda na fase 1

- Prefixo dos arquivos de pack.
- Formato do `manifest.json` (schema 1 continua válido).
- URL do GitHub Pages.
- Nome do repositório.
- Estrutura de `packs.zip` / `packs/`.
- Comportamento de seleção por protocolo.
- `paper-plugin.yml`/`plugin.yml` `main` (a classe continua no pacote antigo).

Motivo: qualquer mudança nesses itens quebra jogadores com cache, manifests antigos ou links já divulgados.

## 3. Ordem de implementação

1. **Constantes e mensagens**
   - `plugin.yml`: `name: FixTextures`, `prefix`, descrição, site.
   - Nome do JAR no Gradle: `FixTextures.jar` (sem renomear o módulo).
   - Mensagens e logs.
2. **Pasta de dados**
   - `getDataFolder()` passa a apontar para `plugins/FixTextures`.
   - Na inicialização, se `plugins/ModernTextures` existir e `plugins/FixTextures` não existir:
     - copiar `config.yml`, `manifest.json`, `packs/`, `packs.zip`, `ModernTextures-Packs.zip`;
     - registrar no log o que foi copiado;
     - **não apagar** a pasta antiga automaticamente;
     - se ambas existirem, usar `FixTextures` e avisar.
3. **Comandos**
   - Registrar `/ft` e `/mt` (alias) no `plugin.yml`.
   - Ajuda mostra `/ft` como principal.
   - Tab completion aceita os dois.
4. **Permissões**
   - `fixtextures.admin` padrão `op`.
   - Verificação aceita `fixtextures.admin` **ou** `moderntextures.admin`.
5. **HTTP embutido**
   - Nenhuma mudança de rota obrigatória.
   - Logs passam a dizer FixTextures.
6. **Tutorial**
   - Copiar `TUTORIAL.md` para a pasta nova se não existir.
7. **Documentação e artefatos**
   - README, docs, `package.ps1`, nomes de ZIP de release.
   - `dist/release/FixTextures.jar` etc.
8. **Fase opcional (depois de tudo estável)**
   - Renomear pacote `dev.moderntextures` → `dev.fixtextures`.
   - Fazer em um commit isolado, sem outras mudanças.
   - Atualizar imports, `plugin.yml`, testes e relatórios.
   - Build completo + smoke Paper.

## 4. Compatibilidade

### Configuração

- Chaves atuais (`base-url`, `embedded-server`, etc.) continuam iguais.
- Novas chaves de variantes são opcionais.
- Config antiga copiada deve carregar sem edição.

### Manifesto

- Schema 1 continua aceito.
- Schema 2 adiciona `variant`, `displayName`; campos antigos permanecem.
- O plugin não deve reescrever o manifesto do usuário sem confirmação.

### Packs publicados

- Nomes e URLs atuais continuam válidos.
- Novos packs devem manter o prefixo `moderntextures-26.3-to-`.
- Se um dia o prefixo mudar, publicar os dois nomes por pelo menos uma versão.

### Jogadores

- Preferência de variante é nova; ausência = variante padrão.
- Nenhum jogador precisa reconectar só por causa do rename, desde que o pack não mude.
- Se o plugin mudar de nome, o cliente pode ver o pack como novo emissor; manter o mesmo `id`/hash do pack evita re-download.

## 5. Riscos

| Risco | Mitigação |
|---|---|
| Dois plugins carregados (`ModernTextures` + `FixTextures`) | detectar no `onEnable` e recusar/avisar |
| Pasta antiga e nova com configs diferentes | usar a nova; logar aviso claro |
| Permissão antiga não reconhecida | aceitar as duas |
| Comando `/mt` conflita com outro plugin | manter alias só por uma versão e documentar |
| URLs quebradas | não renomear arquivos publicados |
| Pacote Java renomeado quebra reflexão/testes | fase separada, busca global, build completo |
| JAR antigo esquecido em `plugins/` | documentar remoção do `ModernTextures.jar` |

## 6. Checklist de aceite

- [ ] `plugins/FixTextures/` criado com `config.yml`, `manifest.json` e `TUTORIAL.md`.
- [ ] Migração copia dados de `plugins/ModernTextures/` sem apagar.
- [ ] `/ft status`, `/ft send`, `/ft reload` funcionam.
- [ ] `/mt` continua funcionando como alias.
- [ ] `fixtextures.admin` e `moderntextures.admin` autorizam.
- [ ] Manifesto schema 1 carrega sem edição.
- [ ] Packs antigos continuam sendo servidos pela mesma URL.
- [ ] Nenhuma skin de jogador alterada.
- [ ] Smoke Paper 26.3 sem exceções.
- [ ] `dist/release/FixTextures.jar` gerado e com SHA-256 registrado.
