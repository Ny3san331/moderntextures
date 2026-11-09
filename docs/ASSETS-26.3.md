# Inventário de assets 26.3

Referência para a implementação do FixTextures. Nada aqui é copiado automaticamente para os packs; é a fonte de consulta.

## Origem

- Site de consulta: <https://mcasset.cloud/> (lê o JAR oficial do cliente).
- Manifesto oficial: <https://piston-meta.mojang.com/mc/game/version_manifest_v2.json>.
- Versão: **26.3** (release, 2026-09-15).
- `downloads.client.url`: `https://piston-data.mojang.com/v1/objects/e877b6a07acd633fb3bb475002175cec036e7b87/client.jar`
- Tamanho: 41.483.720 bytes.
- SHA-1: `e877b6a07acd633fb3bb475002175cec036e7b87`.
- Cópia local: `cache/2a51c07407bb29cc43d49b8ab8d855ec66df5bea` (chave = SHA-1 da URL, usada pelo gerador).
- Entradas no JAR: 34.227.

## Clientes de comparação já em cache

| Versão | Arquivo em `cache/` | Tamanho |
|---|---|---:|
| 1.16.5 | `2498a7433719239f45181a66377b2130a66ab384` | 17.547.153 |
| 1.20.4 | `f44f74e0782f869df4d1cf6e96b866995bf0a403` | 24.445.539 |
| 1.21.4 | `43b15c01e432de515c664948911ba24f6463041b` | 28.335.587 |
| 1.21.8 | `c1ee77c1b2c4a3056c0dfef8f435acb2c0217379` | 29.525.242 |
| 26.1.2 | `be7b77f455929cb4341f7470164240d6dc6b0476` | 38.113.927 |
| 26.2 | `508c29a95c43df904a5dd7fcc77d8a7eadd217a3` | 39.193.383 |
| 26.3 | `2a51c07407bb29cc43d49b8ab8d855ec66df5bea` | 41.483.720 |

## Contagens 26.3

| Categoria | Caminho | Quantidade |
|---|---|---:|
| Todas as texturas | `assets/minecraft/textures/**` | 4.371 |
| Blocos | `textures/block/*.png` | ~1.000 |
| Itens | `textures/item/*.png` | ~1.100 |
| Entidades | `textures/entity/**` | 759 |
| Partículas | `textures/particle/*.png` | 300 |
| Trims | `textures/trims/**` | 75 (36 + 36 + 3 diretórios) |
| Paletas | `textures/palettes/**` | 15 |
| Definições de item | `assets/minecraft/items/*.json` | 1.659 |
| Definições de equipamento | `assets/minecraft/equipment/*.json` | 50 |

Cobertura de partículas por cliente (caminhos em comum com 26.3):

| Cliente | Comuns | Total no cliente |
|---|---:|---:|
| 1.16.5 | 138 | 139 |
| 1.20.4 | 211 | 211 |
| 1.21.4 | 240 | 240 |
| 1.21.8 | 253 | 253 |
| 26.1.2 | 254 | 254 |
| 26.2 | 288 | 288 |

## Entidades

### Caminhos que mudaram de nome (exemplos reais)

| 1.16.5 / 1.20.4 | 26.3 |
|---|---|
| `textures/entity/cat/black.png` | `textures/entity/cat/cat_black.png` |
| `textures/entity/cow/cow.png` | `textures/entity/cow/cow_temperate.png` |
| `textures/entity/cow/cow_baby.png` | `textures/entity/cow/cow_temperate_baby.png` |
| `textures/entity/pig/pig.png` | `textures/entity/pig/pig_temperate.png` |
| `textures/entity/chicken/chicken.png` | `textures/entity/chicken/chicken_temperate.png` |
| `textures/entity/wolf/wolf.png` | `textures/entity/wolf/wolf.png` (mesmo nome, UV mudou na 1.20.5) |
| `textures/entity/villager/villager.png` | `textures/entity/villager/villager.png` + `villager/type/…` + `villager/profession/…` |
| `textures/entity/zombie/zombie.png` | `textures/entity/zombie/zombie.png` + `zombie_baby` + `drowned` + `husk` |

Não montar alias por “nome parecido”. Usar tabela por protocolo.

### Caminhos proibidos

Nunca incluir `assets/minecraft/textures/entity/player/**` no pack. Isso substitui skins de todos os jogadores.

### Camadas de equipamento (26.3)

```
assets/minecraft/textures/entity/equipment/humanoid/{chainmail,copper,diamond,gold,iron,leather,leather_overlay,netherite,turtle_scute}.png
assets/minecraft/textures/entity/equipment/humanoid_baby/…            (9)
assets/minecraft/textures/entity/equipment/humanoid_leggings/…        (8)
assets/minecraft/textures/entity/equipment/horse_body/…               (7)
assets/minecraft/textures/entity/equipment/llama_body/…               (18)
assets/minecraft/textures/entity/equipment/wolf_body/…                (2)
assets/minecraft/textures/entity/equipment/wings/elytra.png
assets/minecraft/textures/entity/equipment/*_saddle/…                 (montarias)
```

Todas as camadas humanoides medidas são **64×32**.

## Armadura antiga

| 26.3 | ≤1.20.4 |
|---|---|
| `entity/equipment/humanoid/<m>.png` | `models/armor/<m>_layer_1.png` |
| `entity/equipment/humanoid_leggings/<m>.png` | `models/armor/<m>_layer_2.png` |
| `entity/equipment/humanoid/leather_overlay.png` | `models/armor/leather_layer_1_overlay.png` |
| `entity/equipment/humanoid/turtle_scute.png` | `models/armor/turtle_layer_1.png` |

Materiais com arte nova na 26.3 (copiar mesmo assim): `chainmail`, `diamond` (peitoral), `netherite` (calça), `turtle_scute`. `copper` não existe como armadura em clientes antigos.

## Trims

### 26.3

```
textures/trims/entity/humanoid/<pattern>.png          (18 padrões)
textures/trims/entity/humanoid/<pattern>.png.mcmeta  {"palette":{"base_palette":"trim_base"}}
textures/trims/entity/humanoid_leggings/<pattern>.png
textures/trims/items/{helmet,chestplate,leggings,boots}_trim.png
textures/palettes/trim_base.png                       (8×1)
textures/palettes/trim/<material>.png                 (8×1)
```

Padrões: `bolt`, `coast`, `dune`, `eye`, `flow`, `host`, `raiser`, `rib`, `sentry`, `shaper`, `silence`, `snout`, `spire`, `tide`, `vex`, `ward`, `wayfinder`, `wild`.

### 1.20.4

```
textures/trims/models/armor/<pattern>.png
textures/trims/models/armor/<pattern>_leggings.png
textures/trims/items/{helmet,chestplate,leggings,boots}_trim.png
textures/trims/color_palettes/trim_palette.png
textures/trims/color_palettes/<material>.png
```

### 1.21.4+

```
textures/trims/entity/humanoid/<pattern>.png
textures/trims/entity/humanoid_leggings/<pattern>.png
textures/trims/color_palettes/…   (até 26.2)
textures/palettes/trim/…          (26.3)
```

### Paletas (medidas)

- Índices usados nas texturas de trim: `#606060`, `#808080`, `#A0A0A0`, `#C0C0C0`, `#E0E0E0`.
- `trim_base` / `trim_palette`: `#000000`, `#202020`, `#404040`, `#606060`, `#808080`, `#A0A0A0`, `#C0C0C0`, `#E0E0E0`.
- Ferro 26.3 = ferro 1.20.4: `#465151`, `#576363`, `#657070`, `#717D7D`, `#7B8989`, `#9DAAAA`, `#BFC9C8`, `#C5D2D4`.
- Ferro escuro 26.3 = ferro escuro 1.20.4: `#1D2828`, `#2B3434`, `#313B3B`, `#3D4949`, `#576363`, `#6F7676`, `#8A9291`, `#A2B0B3`.

Isso permite converter sem recolorir manualmente: renomear arquivos e ajustar o nome da paleta base.

## Lança (item segurado)

Arquivos 26.3:

```
assets/minecraft/items/{wooden,stone,copper,iron,golden,diamond,netherite}_spear.json
assets/minecraft/models/item/<material>_spear.json
assets/minecraft/models/item/<material>_spear_in_hand.json
assets/minecraft/models/item/spear_in_hand.json
assets/minecraft/textures/item/<material>_spear.png
assets/minecraft/textures/item/<material>_spear_in_hand.png
assets/minecraft/textures/gui/sprites/container/slot/spear.png
```

Definição de exemplo (`items/iron_spear.json`):

```json
{
  "model": {
    "type": "minecraft:select",
    "property": "minecraft:display_context",
    "cases": [
      {
        "when": ["gui", "ground", "fixed", "on_shelf"],
        "model": { "type": "minecraft:model", "model": "minecraft:item/iron_spear" }
      }
    ],
    "fallback": { "type": "minecraft:model", "model": "minecraft:item/iron_spear_in_hand" }
  },
  "swap_animation_scale": 1.95
}
```

`models/item/spear_in_hand.json`:

```json
{
  "gui_light": "front",
  "parent": "item/generated",
  "display": {
    "firstperson_righthand":  { "rotation": [-20, 90, -35], "translation": [3.13, 2.0, 0.13], "scale": [1.36, 1.36, 0.68] },
    "firstperson_lefthand":   { "rotation": [-20, -90, 35], "translation": [3.13, 2.0, 0.13], "scale": [1.36, 1.36, 0.68] },
    "thirdperson_righthand":  { "rotation": [5, 270, -40], "translation": [0, 2, 2], "scale": [1.7, 1.7, 0.85] },
    "thirdperson_lefthand":   { "rotation": [5, -270, 40], "translation": [0, 2, 2], "scale": [1.7, 1.7, 0.85] }
  }
}
```

Para clientes antigos, copiar o bloco `display` para um modelo próprio com `parent: item/generated` e `layer0` apontando para `*_spear_in_hand`. `swap_animation_scale` não existe antes da 26.x; o golpe continua o do item substituto.

## Blocos com lacuna conhecida

| Alvo antigo | 26.3 | Ação |
|---|---|---|
| `block/grass_path_side.png` | `block/dirt_path_side.png` | alias |
| `block/grass_path_top.png` | `block/dirt_path_top.png` | alias |
| `block/chain.png` | `block/iron_chain.png` | alias |
| `block/purpur_pillar.png` | `block/purpur_pillar_side.png` + `_top.png` | composição ou manter |
| `block/quartz_pillar.png` | `block/quartz_pillar_side.png` + `_top.png` | composição ou manter |
| `block/grass.png` (1.16.5) | — | legado não usado; ignorar |

## Como extrair um arquivo do JAR

PowerShell:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead('cache\2a51c07407bb29cc43d49b8ab8d855ec66df5bea')
$entry = $zip.GetEntry('assets/minecraft/textures/trims/entity/humanoid/coast.png')
[IO.Compression.ZipFileExtensions]::ExtractToFile($entry, 'coast.png', $true)
$zip.Dispose()
```

Ou abrir <https://mcasset.cloud/> e navegar por versão/caminho, sem baixar o JAR inteiro.

## Direitos

Os assets pertencem à Mojang/Microsoft. O projeto os utiliza em resource packs de servidor, sem vender os assets nem redistribuí-los como produto separado. Manter os créditos no README e no tutorial.
