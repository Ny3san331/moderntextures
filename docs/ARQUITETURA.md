# Arquitetura

## Módulos

- `core`: perfis de protocolo, contratos JSON, validação, seleção e estados de carregamento. Sem dependência Bukkit.
- `generator`: aquisição/cache, conversão de texturas, leitura de dados Via, construção/validação de ZIPs. Aplicação CLI; nenhuma tarefa pesada no tick do servidor.
- `plugin`: adaptação Paper/ViaVersion, envio e acompanhamento por jogador, com Adventure para mensagens. Inclui um servidor HTTP embutido opcional para servir os ZIPs da própria pasta do plugin. Somente APIs públicas; nenhum NMS, reflexão, interceptador de pacotes ou alteração de inventário.

## Fluxo

1. Gerador usa o JAR 26.3 como fonte atual e o JAR de cada cliente como descrição do formato/layout esperado.
2. Renomeações históricas do Prismarine fazem correspondência de caminhos antigos; os caminhos atuais existentes têm prioridade, salvo alias explícito.
3. Texturas de itens/blocos compatíveis substituem recursos nos caminhos do cliente. Modelos vanilla daquele cliente são preservados.
4. Para itens ausentes, percorre registros/diffs Via, resolve o material transmitido e gera seletores de modelo para os dados adicionados pelo ViaBackwards.
5. Para 1.14–1.21.3 gera `overrides` numéricos; o modelo base permanece como fallback natural para qualquer valor não mapeado. Para 1.21.4+ envolve o modelo original em seletores com fallback preservado.
6. Sprites de itens novos ficam em `assets/minecraft/textures/item/moderntextures_*.png`. A partir da 1.21.4 o atlas de itens só varre esse diretório do namespace `minecraft`; usar outro namespace exigiria uma entrada de atlas adicional.
7. O servidor mantém o item original. ViaBackwards continua responsável por tradução e dados de retorno do inventário. O plugin apenas escolhe o pack pelo protocolo.
8. ZIPs têm ordem determinística, timestamps fixos e hashes de conteúdo. Manifesto é atualizado após cada geração concluída.

## Estado por conexão

`detecção -> SENT -> ACCEPTED -> DOWNLOADED -> LOADED`

O cliente pode omitir estados intermediários. Falha/recusa/descarte são terminais. Timeout é uma decisão local: um sucesso tardio da mesma solicitação ainda atualiza o estado para carregado. Um pack carregado pode ser descartado ou falhar em uma recarga posterior. Desconectar cancela timers. Respostas modernas são filtradas pelo UUID do pack. Versões antigas dependem de um único emissor. A recarga da configuração é transacional e não reenvia solicitações pendentes.

## Persistência e limites

O plugin escreve apenas sua configuração/manifesto iniciais dentro de `plugins/ModernTextures`. O gerador escreve apenas nos diretórios `--cache` e `--output`; arquivos de configuração são somente lidos. O conteúdo de ZIP não é extraído para caminhos do sistema. Entradas inseguras/arquivos excessivamente grandes são rejeitados.

`--all` não significa “todos os modelos foram reconstruídos”: o formato de recurso, a existência do material, o modelo e a lógica do cliente são capacidades distintas. O relatório separa cada resultado.
