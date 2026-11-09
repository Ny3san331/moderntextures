# Plano FixTextures — preparação

**Status:** preparação concluída, nenhuma alteração de código feita.
**Data:** 2026-11-09
**Origem:** ModernTextures 1.0.0 (Paper 26.3, Java 25)
**Destino:** FixTextures (mesmo repositório GitHub: `Ny3san331/moderntextures`)

Este documento descreve o que será feito, em que ordem, com quais limites. Ele não substitui a implementação; serve para revisar escopo antes de escrever código.

---

## 1. Situação atual (medida, não estimada)

Pacote gerado para 1.20.4 (`moderntextures-26.3-to-1.20.4-5288b440abb2.zip`):

| Categoria | Texturas no cliente alvo | Texturas no pack | Cobertura |
|---|---:|---:|---:|
| Blocos | 990 | 987 | 99,7% |
| Itens | 587 | 770 (inclui sprites sintéticos de itens novos) | 100% + extras |
| Entidades | 484 | 98 | 20,2% |

Para 1.16.5: blocos 688/694 (99,1%), entidades 74/400 (18,5%).

Amostras de blocos comparadas byte a byte entre o pack 1.16.5, o pack 1.20.4 e o cliente 26.3 (`stone`, `dirt`, `oak_planks`, `grass_block_top`, `glass`): **SHA-1 idêntico**. Ou seja, blocos já são substituídos de verdade; o que falta é fechar 3–6 aliases e tratar texturas que mudaram de formato (pilar dividido em lateral/topo).

O que realmente falta hoje:

1. **Entidades**: `PackBuilder.inScope` só aceita `entity/(chest|bed|signs|shulker|banner|conduit|decorated_pot)` e `enchanting_table_book`. Mobs, jogadores, projéteis e camadas de equipamento ficam de fora.
2. **Armor trims**: nenhuma entrada `assets/minecraft/textures/trims/**` é gerada.
3. **Partículas**: `textures/particle/**` está fora do escopo (300 texturas na 26.3; 211 no cliente 1.20.4).
4. **Itens segurados**: itens novos usam sprite plano no modelo do substituto. A lança, por exemplo, vira uma espada com a textura da lança — exatamente o defeito da imagem enviada.
5. **Várias texturas**: o plugin trabalha com um único manifesto e um único conjunto de packs.
6. **Tutorial**: não existe arquivo explicativo dentro de `plugins/<plugin>/`.
7. **Nome**: ainda é ModernTextures em artefato, `plugin.yml`, pasta, comandos e mensagens.

Referência do cliente 26.3 usado nas medições:

- JAR oficial em cache: `cache/2a51c07407bb29cc43d49b8ab8d855ec66df5bea`
- SHA-1: `e877b6a07acd633fb3bb475002175cec036e7b87`
- 34.227 entradas; 4.371 texturas; 1.659 definições de item; 759 texturas de entidade; 300 de partícula.

---

## 2. Objetivo

Fazer o resource pack — não o plugin — mudar de verdade:

- texturas de **entidades** (mobs, jogadores, projéteis, camadas de equipamento);
- texturas e, quando necessário, **modelos/blockstates de blocos**;
- **armor trims** (padrões, paletas e ícones);
- **postura/modelo de itens segurados** (lança e itens especiais semelhantes);
- **partículas** compatíveis;
- **várias texturas/variantes** selecionáveis;
- tutorial dentro da pasta do plugin;
- renomeação para **FixTextures**;
- publicação no GitHub Pages já usado pelo servidor: `https://ny3san331.github.io/moderntextures/`.

O plugin continua sem alterar inventário, NBT ou pacotes de jogo. Toda aparência vem do resource pack; o plugin só escolhe, entrega e acompanha o pack.

---

## 3. Fora de escopo / limites que não mudam

- **Não é mod de cliente.** Não há como adicionar tipos novos de entidade, bloco ou partícula a um cliente antigo. ViaBackwards traduz o que tem tradução; o pack só retexturiza o que o cliente já sabe desenhar.
- **Animações hardcoded** (uso/ataque da lança, arco, escudo, elytra) não são reproduzíveis por resource pack puro. É possível acertar a pose de `firstperson`/`thirdperson` e a textura; o movimento de ataque continua o do item substituto.
- **Jogadores**: texturas em `textures/entity/player/` **não** serão substituídas. Isso apagaria skins. Só camadas de equipamento (armadura/elytra) entram.
- **Blocos novos sem tradução**: se dois blocos 26.3 caem no mesmo bloco substituto do Via, o pack não pode separá-los. Não sobrescrever o substituto é decisão mantida.
- **HTTPS no modo embutido**: continua exigindo proxy reverso; o servidor embutido é HTTP simples.
- **GitHub Pages** não é CDN: há limites de tamanho de arquivo/repositório e de banda. Para um servidor pequeno é suficiente; para muitos jogadores, planejar CDN depois.

---

## 4. Especificações por recurso

### 4.1 Entidades

**Escopo:** `assets/minecraft/textures/entity/**` (exceto `player/`).

Subgrupos e tratamento:

| Grupo | Exemplos | Tratamento |
|---|---|---|
| Mobs terrestres | zumbi, esqueleto, creeper, aranha, vaca, porco, galinha, ovelha | cópia direta quando o caminho e o layout existem na versão alvo |
| Variantes | `cow_temperate`, `pig_cold`, `cat_black`, `wolf_*` | tabela de aliases por era de versão |
| Aldeões | `villager/type`, `profession`, `profession_level`, `baby` | alias + validação de layout; versões antigas usam caminhos diferentes |
| Equipamento | `entity/equipment/humanoid*`, `horse_body`, `llama_body`, `wolf_body` | ver 4.3 |
| Overlays/emissivos | `*_overlay`, `*_eyes`, `*_outer_layer` | copiar junto do corpo; `.mcmeta` quando animado |
| Projéteis | flechas, tridente | cópia direta |
| Blocos animados já cobertos | baú, cama, placa, shulker, banner, conduit, vaso | manter o comportamento atual |

**Problema central:** proporção igual não prova layout igual. O código atual usa `compatibleLayout` (produto largura×altura). Para entidades isso é insuficiente: o lobo mudou de UV na 1.20.5 mantendo 64×32.

**Solução planejada:**

1. Tabela explícita `entity_alias` por faixa de protocolo (não heurística de nome).
2. Lista segura (`safe_entities`) revisada visualmente por família; o resto entra como “não homologado” no relatório, não no pack.
3. Validação adicional: dimensões + hash conhecido da textura alvo + amostragem de pixels transparentes/opacos nas mesmas coordenadas.
4. Relatório por pack: `entityUpdated`, `entitySkippedAlias`, `entitySkippedLayout`.
5. Nunca tocar em `textures/entity/player/**`.

**Ganho esperado:** 1.20.4 sai de 98 para ~450–480 texturas de entidade; 1.16.5 sai de 74 para ~300–350 (as demais não existem no cliente antigo).

### 4.2 Blocos

Já cobertos em 99%. Ações:

- Fechar aliases reais:
  - `grass_path_side/top` (1.16.5) → `dirt_path_side/top` (26.3);
  - `chain` → `iron_chain` (26.3 separou cobre/ferro);
  - `purpur_pillar` e `quartz_pillar`: 26.3 tem `_side` e `_top`; gerar composição para o formato antigo ou manter a textura antiga e registrar a limitação;
  - `grass.png` em 1.16.5: textura legada não usada; ignorar conscientemente.
- Auditar `textures/block/*.png.mcmeta` (animação) e `gui_light`.
- **Modelos/blockstates**: só entrar quando a geometria 26.3 for compatível com o id antigo. Caso típico: blocos que ganharam variantes de conexão ou formato. Sem isso, textura nova em modelo velho pode ficar esticada.
- **Não** sobrescrever bloco substituto de bloco novo sem tradução (regra atual mantida).

### 4.3 Armadura e armor trims

**Mudança de pipeline na 26.x:**

- 26.3: `assets/minecraft/equipment/<material>.json` + `textures/entity/equipment/humanoid/<material>.png`, `humanoid_baby`, `humanoid_leggings`.
- 1.20.4 e anteriores: `textures/models/armor/<material>_layer_1.png`, `_layer_2.png`, `leather_layer_1_overlay.png`.

**Mapeamento de armadura:**

| 26.3 | 1.20.4 / 1.16.5 |
|---|---|
| `entity/equipment/humanoid/iron.png` | `models/armor/iron_layer_1.png` |
| `entity/equipment/humanoid_leggings/iron.png` | `models/armor/iron_layer_2.png` |
| `entity/equipment/humanoid/leather_overlay.png` | `models/armor/leather_layer_1_overlay.png` |
| `entity/equipment/humanoid/turtle_scute.png` | `models/armor/turtle_layer_1.png` |
| `entity/equipment/humanoid_baby/*` | mesma textura de adulto (cliente antigo escala) |

Todas as texturas medidas são 64×32. Algumas mudaram de arte na 26.3 (chainmail, diamond layer 1, netherite leggings, turtle), então **copiar é o objetivo**, não comparar igualdade.

**Trims — formato novo (26.3):**

- `textures/trims/entity/humanoid/<pattern>.png` + `.png.mcmeta` com `{"palette":{"base_palette":"trim_base"}}`
- `textures/trims/entity/humanoid_leggings/<pattern>.png`
- `textures/trims/items/{helmet,chestplate,leggings,boots}_trim.png`
- `textures/palettes/trim_base.png` (8×1)
- `textures/palettes/trim/<material>.png` (8×1)

**Trims — formatos antigos:**

| Versão | Modelo | Paletas |
|---|---|---|
| 1.20–1.20.4 | `trims/models/armor/<pattern>.png` e `<pattern>_leggings.png` | `trims/color_palettes/<material>.png` + `trim_palette.png` |
| 1.21.4–26.2 | `trims/entity/humanoid[_leggings]/<pattern>.png` | `trims/color_palettes/*` |

**Conversão:** as texturas usam índices de paleta `0x60, 0x80, 0xA0, 0xC0, 0xE0`; as paletas 26.3 e 1.20.4 medidas têm exatamente as mesmas 8 cores (ex.: ferro `#465151 … #C5D2D4`). Portanto:

- 1.20.4: copiar PNG + `.mcmeta` não é necessário; renomear para `<pattern>.png` / `<pattern>_leggings.png`; copiar `palettes/trim_base.png` → `color_palettes/trim_palette.png` e `palettes/trim/<m>.png` → `color_palettes/<m>.png`.
- 1.21.4–26.2: caminhos `entity/humanoid*` são os mesmos; ajustar nome da paleta base.
- Validar pixel a pixel com as paletas de referência antes de publicar.

**Padrões:** 26.3 tem 18 (`bolt`, `coast`, `dune`, `eye`, `flow`, `host`, `raiser`, `rib`, `sentry`, `shaper`, `silence`, `snout`, `spire`, `tide`, `vex`, `ward`, `wayfinder`, `wild`). 1.20.4 conhece 16; `bolt` e `flow` só entram em clientes que já os conhecem. O ícone de item pode ser atualizado em todos.

**Armadura de cobre** é item novo; clientes antigos não a têm. Não gerar caminho `copper_layer_1` para versões sem o item.

### 4.4 Item segurado / lança

Definição 26.3 (`assets/minecraft/items/iron_spear.json`):

- seletor `minecraft:display_context` (`gui`, `ground`, `fixed`, `on_shelf` → modelo de inventário; demais → `_in_hand`);
- `swap_animation_scale: 1.95`;
- `models/item/iron_spear.json` → `item/generated` + sprite `item/iron_spear`;
- `models/item/iron_spear_in_hand.json` → pai `item/spear_in_hand` + sprite `item/iron_spear_in_hand`;
- `models/item/spear_in_hand.json` define `gui_light: front` e `display` de primeira/terceira pessoa.

**Abordagem para clientes antigos (1.14–1.21.3):**

- modelo próprio com `parent: item/generated`, `gui_light: front`, `display` copiado de `spear_in_hand`;
- `layer0` = sprite `*_spear_in_hand`;
- override por `custom_model_data` no item transportador do Via;
- para 1.21.4+, usar o seletor de componente já existente.

**Limite:** `swap_animation_scale` e as animações de uso/ataque da lança são código do cliente. A pose parada e a textura ficam corretas; o movimento de golpe continua o do substituto. Isso deve aparecer no relatório e no tutorial.

**Itens semelhantes:** mace? trident? Use a mesma estratégia para qualquer item 26.3 com `_in_hand`/`display_context` próprio. A lista será derivada das definições `assets/minecraft/items/*.json` que usam `display_context` ou `swap_animation_scale`.

### 4.5 Partículas

- Copiar somente `textures/particle/*.png` cujo caminho exista no cliente alvo.
- Caminhos comuns: 138 (1.16.5), 211 (1.20.4), 240 (1.21.4), 253 (1.21.8), 254 (26.1.2), 288 (26.2).
- Respeitar `.png.mcmeta` (ex.: `vibration`).
- Partículas novas (`geyser_*`, `sga_*`, `noxious_gas_*`, `sulfur_cube_goo`, `copper_fire_flame`) não existem em clientes antigos; não há como criá-las por pack.
- Não alterar cores por tintura; só textura.

### 4.6 Várias texturas (variantes)

Objetivo: o servidor poder oferecer mais de um conjunto (ex.: `default`, `pvp`, `medieval`) e o jogador escolher.

Proposta de layout:

```text
plugins/FixTextures/
  config.yml
  manifest.json              # variante padrão (compatível com o formato atual)
  packs.zip                  # bundle padrão (opcional)
  variants/
    default/
      manifest.json
      packs/                 # zips individuais
    pvp/
      manifest.json
      packs/
  TUTORIAL.md
```

Manifesto v2 (retrocompatível):

```json
{
  "schema": 2,
  "sourceVersion": "26.3",
  "variant": "default",
  "displayName": "FixTextures padrão",
  "packs": [
    { "clientVersion": "1.20.4", "protocol": 765, "file": "...zip", "sha1": "..." }
  ]
}
```

Plugin:

- `variants.default` no `config.yml`;
- `allow-player-choice: true|false`;
- `/ft texture list` e `/ft texture <nome>` (Adventure, lista clicável);
- preferência por jogador persistida em `players.yml` (UUID → variante);
- envio usa o manifesto da variante escolhida;
- HTTP embutido ganha rota `/packs/<variante>/<arquivo>`;
- se o jogador não escolheu, usa a padrão;
- manifesto v1 continua aceito.

Limite: um cliente só mantém um pack ativo por vez; trocar de variante reenvia o pack e pode exigir reconexão em versões antigas. O tutorial explica isso.

### 4.7 Tutorial dentro da pasta do plugin

- Conteúdo canônico: `docs/TUTORIAL-TEXTURAS.md` (neste repositório) e cópia em `plugin/src/main/resources/TUTORIAL.md`.
- Na primeira execução, o plugin copia para `plugins/FixTextures/TUTORIAL.md` se não existir.
- O arquivo explica: estrutura do pack, como trocar uma textura, como publicar no GitHub, como atualizar manifesto/SHA-1, limites e solução de problemas.
- Não sobrescrever edição do usuário.

### 4.8 Renomeação para FixTextures

Ordem segura (detalhes em `docs/MIGRACAO-FIXTEXTURES.md`):

1. `plugin.yml` (`name`, `main` inalterado), artefato `FixTextures.jar`, pasta `plugins/FixTextures`.
2. Comando principal `/ft`, mantendo `/mt` como alias temporário.
3. Permissão `fixtextures.admin`, aceitando `moderntextures.admin` como legada.
4. Mensagens, README, docs e relatórios.
5. Migração automática de `plugins/ModernTextures` para `plugins/FixTextures` (copiar config/manifest/packs, nunca apagar sem confirmação).
6. Pacote Java `dev.moderntextures` **não muda na primeira fase**; renomear pacote é mecânico, mas arriscado e sem ganho funcional. Fica para uma fase opcional.
7. Prefixo dos arquivos de pack (`moderntextures-26.3-to-...`) **não muda** para não quebrar URLs e manifests já publicados. A marca visual muda; o nome técnico do arquivo permanece.

O repositório GitHub continua `Ny3san331/moderntextures`; a URL pública `https://ny3san331.github.io/moderntextures/` permanece.

---

## 5. Impacto por módulo (sem implementar)

### `core`

- `PackManifest`: schema 2, variante e nome de exibição.
- Novo `Variants`: descoberta/validação de variantes.
- Novo `EntityAliases`: tabela versão → caminho antigo → caminho 26.3.
- Novo `TrimPalette`: conversão de paletas e nomes.
- `MappingConfig`: listas `safe_entities`, `held_items`, `variants`.

### `generator`

- `PackBuilder`: ampliar `inScope`, separar builders por categoria.
- Novos: `EntityBuilder`, `TrimBuilder`, `ParticleBuilder`, `HeldItemBuilder`, `BlockAliasBuilder`.
- Relatórios: contagens por categoria e motivos de exclusão.
- Validação: PNG, referências, tamanho, `mcmeta`, colisões.

### `plugin`

- `config.yml`: variantes, tutorial, nome.
- Comandos `/ft` (+ alias `/mt`), seleção de variante, status por categoria.
- `PackHttpServer`: rotas por variante.
- Extração do tutorial na primeira execução.
- Migração de pasta antiga.

---

## 6. Aquisição de assets (mc assets)

Fonte usada: **MC Assets** (`https://mcasset.cloud/`), que lê o JAR oficial do cliente. Para automação/reprodutibilidade, o caminho oficial é o manifesto da Mojang:

1. `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`
2. Entrada `26.3` → JSON da versão.
3. `downloads.client.url` → JAR (41.483.720 bytes, SHA-1 `e877b6a0…`).
4. Ler o JAR como ZIP; nunca executar.

O gerador já faz exatamente isso e mantém cache por SHA-1 da URL. O JAR 26.3 e os clientes 1.16.5, 1.20.4, 1.21.4, 1.21.8, 26.1.2 e 26.2 já estão em `cache/`.

Referência extraída para consulta: `dist/reference/26.3/` (itens de lança, equipamento, trims, paletas, partículas e amostras de entidades). **Não** é copiada automaticamente para os packs; serve para conferência e para o desenvolvimento.

---

## 7. Matriz de versões

| Versão | Pack format | Entidades | Armadura | Trims | Partículas |
|---|---:|---|---|---|---|
| 1.7.10 / 1.8.9 | 1/2 | caminhos legados, pouca cobertura | `models/armor` | não existem | poucas |
| 1.12.2 | 3 | `textures/entity` | `models/armor` | não existem | poucas |
| 1.16.5 | 5/6 | `textures/entity` | `models/armor` | não existem | 139 |
| 1.20.4 | 22 | `textures/entity` | `models/armor` | `trims/models/armor` + `color_palettes` | 211 |
| 1.21.4 | 46 | `textures/entity` | `entity/equipment` | `trims/entity` + `color_palettes` | 240 |
| 1.21.8 | 55 | `textures/entity` | `entity/equipment` | `trims/entity` | 253 |
| 26.1.2 / 26.2 | 70/73 | `textures/entity` | `entity/equipment` | `trims/entity` | 254/288 |
| 26.3 (nativo) | 80 | origem | origem | origem | origem |

Formatos de pack exatos vêm de `version.json` de cada cliente; a tabela é referência de planejamento, não substitui o gerador.

---

## 8. Fases de implementação (ordem sugerida)

1. **Aliases de bloco** (fecha 99,7% → 100%): baixo risco, ganho imediato.
2. **Entidades — famílias seguras**: zumbi, esqueleto, creeper, aranha, enderman, vaca, porco, galinha, ovelha. Tabela de aliases + relatório.
3. **Equipamento e armadura**: `models/armor` para ≤1.20.4; `entity/equipment` para ≥1.21.4.
4. **Trims**: conversão de paleta e caminhos; testes com 1.20.4 e 1.21.4.
5. **Lanças/itens segurados**: modelo `_in_hand` + display + CMD.
6. **Partículas**.
7. **Entidades restantes** (aldeões, variantes, bebês, montarias), com homologação visual.
8. **Variantes** (manifesto v2, comando, HTTP).
9. **Renomeação FixTextures** + tutorial + migração.
10. **Publicação** dos packs no GitHub Pages e smoke final.

Cada fase termina com build, testes e um pack de amostra antes de seguir.

---

## 9. Testes e critérios de aceite

Automatizados:

- aliases: cada caminho antigo tem origem 26.3 existente ou é explicitamente ignorado;
- trims: paleta convertida com as 8 cores esperadas; PNG 64×32; nomes antigos existentes;
- entidades: nenhum `player/**`; nenhum caminho sem origem; contagem por categoria no relatório;
- lança: JSON válido, `display` presente, sprite referenciado existe;
- variantes: manifesto v1 e v2 aceitos; variante inexistente rejeitada;
- HTTP: rota por variante, `HEAD`, `Range`, `404` fora do manifesto;
- ZIP: `pack.mcmeta` na raiz, sem `..`, sem caminho absoluto, sem duplicata.

Visuais (manuais, clientes reais):

| Cliente | Blocos | Entidades | Armadura | Trims | Lança | Partículas |
|---|---|---|---|---|---|---|
| 1.8.9 | x | x | x | n/a | n/a | x |
| 1.16.5 | x | x | x | n/a | x | x |
| 1.20.4 | x | x | x | x | x | x |
| 1.21.4 | x | x | x | x | x | x |
| 1.21.8 | x | x | x | x | x | x |
| 26.3 nativo | x | x | x | x | x | x |

Aceite mínimo:

- 1.20.4: blocos 990/990 (ou exceções documentadas), entidades ≥ 90% dos caminhos comuns, trims 16 padrões, lança com pose correta parada;
- 1.16.5: blocos 694/694 (ou exceções), entidades ≥ 70% dos comuns, lança sem “espada com textura de lança”;
- nenhuma skin de jogador alterada;
- nenhum pack rejeitado por JSON/PNG inválido.

---

## 10. Riscos e decisões abertas

| Risco | Mitigação |
|---|---|
| Layout de entidade muda sem mudar dimensão | lista segura + validação visual + relatório |
| GitHub Pages com banda/limite | packs pequenos; CDN depois; não apagar packs antigos |
| Servidor só tem uma porta | manter `base-url` externo; modo embutido é opcional |
| Animações hardcoded | documentar como limitação; pose aproximada |
| Trims novos (`bolt`, `flow`) | só clientes que os conhecem |
| Renomear pacote Java quebra imports | fase separada, mecânica, com build completo |
| Perda de compatibilidade com URLs antigas | manter prefixo `moderntextures-` nos arquivos |
| Direitos dos assets | manter créditos e não vender assets; repositório público já usado para packs |

---

## 11. Commits previstos

Todos no repositório `https://github.com/Ny3san331/moderntextures`.

1. `docs: preparação FixTextures` (este conjunto de documentos).
2. `core: aliases de bloco e entidade`.
3. `generator: entidades e equipamento`.
4. `generator: armor trims`.
5. `generator: itens segurados (lança)`.
6. `generator: partículas`.
7. `core+plugin: variantes de textura`.
8. `plugin: renomear para FixTextures + tutorial`.
9. `packs: publicar nova geração`.
10. `docs: resultados de homologação`.

Os commits 2–9 só acontecem depois da aprovação deste plano.
