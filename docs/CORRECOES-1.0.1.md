# FixTextures 1.0.1: correções e diagnóstico do cliente 1.16.5

## Corrigido no código

- Blocos novos com templates vanilla `cube_all`, `cube_column` ou `cube_bottom_top` passam a usar modelos 3D no inventário/mão. Ametista e calcita deixam de ser sprites planos.
- Seletores numéricos legados voltam ao modelo base depois do valor exato de CustomModelData. Predicados antigos são limites `>=`, e não igualdade: antes podiam capturar valores de outros itens.
- Ferramentas com parent `handheld` mantêm essa pose; a mace recebe as transformações de mão do asset vanilla.
- O envio em clientes legados usa `addResourcePack`, evitando a remoção prévia de todos os packs feita por `setResourcePack` e a recarga adicional associada.
- Troca de variante aguarda carregamento pendente e evita reenviar a mesma variante já carregada.
- `/ft status <jogador>` informa arquivo, hash e, até 1.16.5, limite de altura e coordenada Y.
- Mensagens de falha deixam de afirmar que o cliente está usando texturas padrão: outro emissor pode ter substituído o pack.

## Evidências do log enviado

O cliente carregou o pack FixTextures com SHA-1 `34449955caffa334935af235db2a5f6af87620bd`. Às 15:32:31 também recebeu outro pack, SHA-1 `f2d4dd5bf8ee221234b738236099b2592c58b8e8`, que passou a ser o pack ativo. A 1.16.5 não empilha packs enviados pelo servidor. Identifique o segundo emissor em `server.properties`, plugins ou proxy. Use um único emissor/pack mesclado para esse cliente. A atualização não desliga outros plugins automaticamente.

O log também mostra erros repetidos `NoClassDefFoundError: dev/tr7zw/skinlayers/renderlayers/HeadLayerFeatureRenderer` na integração entre Entity Texture Features 4.3.1 e 3D Skin Layers 1.11.4, antes da conexão. Desativar temporariamente 3D Skin Layers no perfil permite testar esse conflito. Isso não exige ETF para os assets vanilla do FixTextures. O efeito sobre FPS precisa ser medido no computador do jogador.

Erros de equipes/objetivos duplicados (`stellarity.empre`, `stellarity.drago`, `refresh_player_h`) aparecem na primeira conexão. Esses nomes têm 16 caracteres e sugerem colisões de nomes truncados na tradução para o cliente antigo; é necessário verificar os nomes completos no servidor. FixTextures não cria esses scoreboards.

## Limites que permanecem

- A 1.16.5 vanilla só representa blocos entre Y=0 e Y=255. Se o vazio começa fora desse intervalo, texturas não ampliam o limite: use cliente moderno ou solução adicional de cliente/tradução. Nenhum mod de altura foi implementado.
- Blocos novos no mundo, como ametista traduzida para purpur, não carregam CustomModelData. Aplicar uma textura ao purpur mudaria também o purpur legítimo. Os modelos 3D corrigidos são para itens, não uma reconstrução dos estados perdidos na tradução.
- Mobs que não existem no cliente continuam usando modelos de substitutos do Via. Assets PNG não criam novos renderizadores ou novas entidades.
- A lança recebe sprite/pose, mas o resource pack não implementa alcance, carregamento ou animação de ataque nativa de uma versão mais nova. Em clientes legados, o mesmo modelo é usado no inventário e na mão.
- Testes automatizados validam geração/seletores. Não há homologação visual nem medição de FPS no cliente do usuário.

## Atualização

Troque `FixTextures.jar` com o servidor parado. Atualize `plugins/FixTextures/manifest.json` com o manifesto publicado e mantenha `base-url: 'https://ny3san331.github.io/moderntextures/'`. Se usar bundle local, atualize também `packs.zip`. Reinicie e confira `/ft status <jogador>`. O nome/hash novo evita reutilizar a versão antiga do ZIP.
