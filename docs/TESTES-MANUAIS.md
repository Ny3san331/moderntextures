# Homologação com servidor e clientes

Os testes de build/geração não substituem este roteiro. Um smoke test foi executado com Paper 26.3 build 245 real, ViaVersion/ViaBackwards 5.12.2, 51 perfis no manifesto e `/mt reload`; o plugin habilitou e desabilitou sem exceções. Também foi validado o servidor HTTP embutido servindo `packs.zip` com `GET 200`, `HEAD`, `Range 206` e `404`. Nenhum cliente gráfico real foi conectado, portanto a validação visual continua pendente.

## Preparação

1. Servidor de teste Paper 26.3, Java 25, ViaVersion/ViaBackwards 5.12.2 e ViaRewind 4.2.0.
2. Pack hospedado e `manifest.json` correspondente. Começar sem outros plugins de itens ou packs.
3. Confirmar no log protocolo real e perfil. Se jogadores antigos aparecem como 777, rever onde está a tradução no proxy/backend.

## Matriz mínima

Testar clientes representantes 1.7.10, 1.8.9, 1.12.2, 1.13.2, 1.14.4, 1.16.5, 1.20.4, 1.21.3, 1.21.4, 1.21.10, 1.21.11, 26.1.2, 26.2 e o nativo 26.3. Antes de anunciar suporte integral, completar todos os protocolos oferecidos na hospedagem.

## Casos

- Aceitar, recusar, interromper download, URL indisponível, ZIP inválido e carregamento lento.
- Reconectar durante download; verificar cancelamento de tarefas da sessão antiga.
- Verificar `/mt status`; aceito/baixado não deve aparecer como LOADED.
- Renomear um item na bigorna e mudar o idioma do cliente: textura permanece igual.
- Texturas de blocos existentes em chunks novos/antigos e após colocar/quebrar.
- Itens na mão, mão secundária quando suportada, inventário, cursor, baú compartilhado, equipamento de outro jogador e itens no chão.
- Transferir, dividir e juntar pilhas; shift-click; inventário criativo; morrer e recuperar itens. Confirmar tipo/componentes reais no servidor antes e depois.
- Comparar item novo com item substituto legítimo: o segundo deve manter seu modelo normal.
- Arcos, escudos, bússolas, relógios, itens tintados e animados: verificar fallback dos modelos originais.
- Blocos novos que compartilham substituto: confirmar limitação do relatório, sem alegar identificação individual.
- Para <1.17: verificar restrições de altura do Via, sem atribuí-las ao pack.
- Com outros plugins: repetir os casos com seus componentes customizados e sua ordem de packs.

Registrar build do servidor, versões Via, versão do cliente, SHA-1 do pack, resultado e captura de tela. Consultar `latest.log` do cliente para texturas/modelos ausentes. Só depois marcar esse perfil como homologado.
