# Configuração

## Plugin: `plugins/ModernTextures/config.yml`

| Campo | Significado |
|---|---|
| `base-url` | Diretório HTTP(S) contendo os ZIPs. Vazio usa o servidor embutido (se `public-url` estiver definido). Sem query, fragmento ou credenciais. |
| `prompt` | Texto da solicitação, quando suportado pelo cliente. |
| `timeout-seconds` | 10–1800; padrão 120. |
| `detect-attempts` | 1–100 tentativas de obter o protocolo; padrão 20. |
| `detect-interval-ticks` | 1–100 ticks entre tentativas; padrão 5. |
| `notify-player` | Mensagem de fallback em recusa/falha/timeout. |
| `embedded-server.enabled` | Inicia um servidor HTTP somente-leitura dentro do plugin. Requer porta TCP pública adicional. |
| `embedded-server.bind` | Endereço de escuta; padrão `0.0.0.0`. |
| `embedded-server.port` | 1–65535; deve ser a porta interna da alocação, não a porta do Minecraft. |
| `embedded-server.public-url` | URL que os jogadores usam, por exemplo `http://IP:8080/`. Obrigatória quando `base-url` está vazio. |

O modo embutido procura os packs em `plugins/ModernTextures/packs/` e, se não houver arquivos, em `plugins/ModernTextures/packs.zip` ou `ModernTextures-Packs.zip`. Se `manifest.json` estiver vazio, o plugin tenta ler o manifesto de dentro do bundle. Só nomes listados no manifesto são servidos; não há listagem de diretório. `HEAD` e `Range` são suportados.

`manifest.json` é produzido pelo gerador. Associa cada protocolo a um arquivo e SHA-1 exatos. Duplicatas, protocolos desconhecidos e caminhos inválidos são rejeitados. Não há escolha por “versão mais próxima”, pois ela poderia enviar um formato incompatível.

## Gerador: `mappings.json`

- `schema`: 1.
- `sourceVersion`: 26.3 nesta implementação.
- `prismarineCommit`, `viaMappingsCommit`: hashes completos de commits imutáveis.
- `autoItems`: descobre itens novos com sprite simples e mapeamento resolvível. A versão fixada dos dados deve corresponder ao Via instalado.
- `textureAliases`: caminho da textura **no cliente antigo** → caminho da textura **na 26.3**. Caminhos relativos completos a partir de `assets/minecraft/textures/`, com extensão `.png`.
- `items`: exceções explícitas, sobrepondo a regra automática do mesmo item/perfil.

Exemplo de regra (insira no array `items`; os valores de protocolo devem ser verificados ao alterar o catálogo Via):

```json
{
  "source": "minecraft:iron_spear",
  "substitute": "minecraft:iron_sword",
  "clients": ["1.20.4", "1.21.10"],
  "texture": "assets/minecraft/textures/item/iron_spear.png",
  "model": "handheld",
  "customModelData": 821,
  "originalIdentifier": "iron_spear",
  "stringIndex": 0
}
```

`substitute` é o item **que o Via realmente transmite**, não uma ordem para o plugin alterar o material. Um substituto incorreto fará a condição não ser aplicada. `texture` decide a aparência; `generated` e `handheld` são os pais vanilla suportados para modelos simples.

Para clientes 1.14–1.21.3 usa-se o valor numérico `customModelData`. Para 1.21.4+ usa-se `originalIdentifier` na posição `stringIndex` da lista de strings do componente. São dados internos (componente/NBT); nome exibido, lore, idioma e renomeação não são consultados. As condições modernas aceitam o identificador com ou sem `minecraft:`.

O ViaBackwards 5.12.2 grava o identificador original em `custom_model_data` quando `pass-original-item-name-to-resource-packs: true`. Esse é o dado usado pelo pack. Resource packs não conseguem consultar NBT arbitrário; para tags `custom_data` próprias seria necessário o servidor escrevê-las e clientes 1.21.5+ (propriedade `minecraft:component`), o que este projeto não faz.

O índice automático é 0 para itens vanilla. Se outro plugin inserir strings antes da tradução, configure o índice conhecido ou desative a regra. Não há teste de “contém em qualquer posição” genérico nos seletores usados aqui.

Após alterar regras: **regenere os packs**, publique os novos ZIPs, copie o novo manifesto e use `/mt reload`. Alterar apenas o manifesto não gera modelos.

## Relatório de cobertura

- `updatedTextures`: texturas substituídas por correspondência direta/renomeação.
- `unchangedNoRecentTexture`: manteve recurso do cliente.
- `incompatibleUvLayout`: proporção incompatível; manteve o recurso do cliente.
- `prismarine26_1FallbackNot26_3`: fallback explicitamente identificado.
- `adaptedItems`: sprites condicionais gerados.
- `unadaptedItems`: novos itens sem tradução resolvível/sprite/modelo do substituto.
- `newWorldBlocksUsingViaFallback`: blocos novos que continuam representados pelo Via.
- `worldBlockCatalogAvailable`: se `false`, lista de blocos vazia **não significa** cobertura total.
- `conditionalItemModelsAvailable`: `false` antes da 1.14.

O catálogo de tradução do gerador entende mapeamentos explícitos simples e substituições de materiais por palavras. Casos especiais de reescrita runtime não são inferidos e não devem ser considerados homologados por esse relatório.
