# FixTextures

Plugin **Paper 26.3 / Java 25** que escolhe e envia resource packs com texturas da 26.3 para clientes antigos. Inclui gerador independente, catálogo editável e relatórios de cobertura.

Inclui entidades, armaduras, armor trims, partículas, aliases de blocos e pose própria de lança. Recursos incompatíveis continuam identificados nos relatórios. O tutorial é instalado em `plugins/FixTextures/TUTORIAL.md`.

Variantes ficam em `plugins/FixTextures/variants/<nome>/manifest.json`, com ZIPs na subpasta `packs/`. Use `/ft texture list` e `/ft texture <nome>`. Para gerar outra variante, acrescente `--variant <nome> --display-name <texto>` ao gerador. A hospedagem padrão do projeto é https://ny3san331.github.io/moderntextures/.

## Entrega

- `plugin/build/libs/FixTextures.jar`: plugin do Paper 26.3+.
- `generator/build/libs/FixTextures-Generator.jar`: gerador executável Java 25.
- `mappings.json`: mapeamentos e commits das fontes.
- `packs/`: ZIPs gerados, manifesto e relatórios por cliente.
- `docs/`: configuração, arquitetura e testes manuais.

**Não é um mod de cliente.** Atualiza texturas compatíveis de itens/blocos existentes; representa muitos itens novos por sprites condicionais. Blocos novos que colidem no mesmo estado substituto do ViaBackwards permanecem com a representação padrão desse substituto. Não promete todos os conteúdos com fidelidade em todos os clientes.

## Instalação

1. Use **Paper 26.3 com JDK 25**. O JAR é compilado contra `paper-api` e usa Adventure; não é compatível com Spigot.
2. Instale **ViaVersion 5.12.2**, **ViaBackwards 5.12.2** e, para 1.7/1.8, **ViaRewind 4.2.0** no **Paper**, junto de `FixTextures.jar`.
3. Se houver BungeeCord, mantenha uma build compatível com os protocolos desejados. A configuração recomendada traduz no backend Paper; não instale a tradução simultaneamente no proxy e backend. O plugin não é um plugin BungeeCord.
4. Reinicie o servidor. O plugin cria `plugins/FixTextures/config.yml`, o tutorial e um manifesto vazio.
5. Gere os packs (ou use os entregues em `packs/`). Hospede os **ZIPs** em HTTP(S), em URLs diretas, sem autenticação/intersticial.
6. Copie `packs/manifest.json` para `plugins/FixTextures/manifest.json` e configure `base-url: 'https://ny3san331.github.io/moderntextures/'`.
7. Confirme manualmente no ViaBackwards: `pass-original-item-name-to-resource-packs: true`. Para destinos anteriores à 1.21.4, mantenha também `map-custom-model-data: true`.
8. Execute `/mt reload` e reconecte, ou `/mt send NomeDoJogador`.

O plugin não configura hospedagem, não modifica `server.properties`, arquivos do Via ou do proxy. Evite outro emissor de packs para os mesmos jogadores; clientes antigos têm limitações de empilhamento e identificação do pack. Não configure simultaneamente um pack conflitante em `server.properties`.

## Servidor HTTP embutido (sem host externo)

O cliente Minecraft sempre baixa o pack por HTTP. Se você não tem CDN/site, o próprio plugin pode servir os ZIPs:

1. Envie `FixTextures-Packs.zip` para `plugins/FixTextures/packs.zip` (ou extraia os 51 ZIPs em `plugins/FixTextures/packs/`).
2. O `manifest.json` pode continuar vazio: o plugin lê o manifesto de dentro do `packs.zip`. Se preferir, copie `packs/manifest.json`.
3. No painel, crie uma **alocação/porta TCP adicional**, por exemplo `8080`. Não use a porta do Minecraft.
4. Em `plugins/FixTextures/config.yml`:

```yaml
base-url: ''
embedded-server:
  enabled: true
  bind: '0.0.0.0'
  port: 8080
  public-url: 'http://IP_DA_ALOCACAO:8080/'
```

5. Reinicie e execute `/mt reload`.
6. Teste `http://IP_DA_ALOCACAO:8080/<arquivo-do-manifesto>.zip` no navegador.

O servidor embutido só entrega arquivos listados no manifesto, aceita `HEAD`/`Range` e não lista diretórios. Ele usa HTTP simples; o SHA-1 do pack continua protegendo a integridade. Para HTTPS, coloque um proxy reverso na frente e use `base-url` com o endereço `https://`.

**Limite importante:** se o painel só libera a porta do Minecraft, o modo embutido não funciona. Nesse caso use hospedagem HTTP externa ou peça uma segunda alocação ao provedor.

## Gerar packs

```text
java -jar FixTextures-Generator.jar --list
java -jar FixTextures-Generator.jar --targets 1.8.9,1.20.4,1.21.10,26.2 --config mappings.json --output packs --cache cache
java -Xmx1536M -jar FixTextures-Generator.jar --all --config mappings.json --output packs --cache cache
```

Execute no diretório que contém `mappings.json`, ou informe seu caminho. `--all` baixa os clientes oficiais dos perfis (~gigabytes de cache), gera um pack por protocolo e pode demorar. Downloads concluídos são reutilizados. Downloads oficiais têm SHA-1 verificado. Reexecutar depois de uma falha reutiliza o cache e atualiza o manifesto a cada pack concluído.

Somente `--output` e `--cache` recebem escrita do gerador. As URLs de fontes são fixas, os commits estão na configuração e os JARs baixados são **lidos como ZIP**, nunca executados. Os nomes dos ZIPs incorporam o hash para evitar cache antigo. Não apague ZIPs antigos de uma hospedagem enquanto jogadores ainda os estiverem baixando.

### Versões

Há um perfil por protocolo de release conhecido entre **1.7.2–1.7.10 e 26.2**, abrangendo os protocolos das famílias intermediárias. `--list` informa o representante exato aceito pelo gerador; por exemplo, `1.21.10` representa o protocolo compartilhado com 1.21.9. O cliente nativo 26.3 não recebe pack.

Isso é **cobertura de geração/seleção**, não certificação visual de todas as versões. Snapshots, versões anteriores a 1.7.2 e Bedrock não são atendidos. A conexão depende de ViaVersion/ViaBackwards/ViaRewind, do proxy e das restrições configuradas pelo administrador.

## Comandos

Permissão administrativa: `fixtextures.admin` (padrão: operadores); `moderntextures.admin` é aceito para migração. `/ft texture` permite seleção pessoal. `/mt` permanece como alias.

- `/mt status [jogador]`: protocolo, estado do pack e diagnóstico.
- `/mt send jogador`: detecta a versão e envia o perfil; não duplica envio pendente.
- `/mt reload`: valida configuração e manifesto; se inválidos, mantém a configuração anterior. Não reenvia packs automaticamente.

`ACCEPTED` e `DOWNLOADED` não significam pack aplicado. Apenas `SUCCESSFULLY_LOADED` é sucesso. Recusa, falha e timeout mantêm o jogador conectado com a aparência padrão. Nenhum ItemStack real é substituído.

## Identificação por dados, não por nome

As condições dos modelos usam **`custom_model_data`** (componente/NBT), nunca o nome exibido, lore ou idioma. Renomear na bigorna ou traduzir o cliente não muda a textura. Na 1.14–1.21.3 o seletor é o valor numérico de `custom_model_data`; na 1.21.4+ é a lista de strings do componente. O ViaBackwards 5.12.2 preenche esse dado com o **identificador original** do item (por exemplo, `poplar_boat`), não com o nome visível.

Resource packs não leem NBT arbitrário. Uma tag própria (por exemplo, `custom_data.ModernTextures`) só funcionaria em clientes 1.21.5+ via propriedade `minecraft:component` e se o servidor já escrevesse essa tag em cada ItemStack; este plugin não altera itens. Para o fluxo ViaBackwards, `custom_model_data` é o dado correto e já independe do nome.

## Limitações principais

- **Antes da 1.14**, itens novos não podem ser diferenciados pelo CustomModelData usado aqui. Recebem o visual do substituto Via. Texturas de itens/blocos existentes ainda são atualizadas.
- Itens novos são representados por **sprites planos aproximados**; modelos especiais, tintas, estados de uso e animações não são reconstruídos integralmente.
- Os seletores automáticos modernos pressupõem itens vanilla sem `custom_model_data` preexistente. Outros plugins podem mudar índices, componentes ou o modelo escolhido. Regras manuais permitem ajustar o índice. Packs conflitantes precisam ser mesclados externamente.
- Blocos novos no mundo não têm CustomModelData. O gerador **não sobrescreve indiscriminadamente o bloco substituto** com a textura de um bloco novo. Isso mudaria também todos os blocos legítimos daquele tipo.
- A identidade de bloco já perdida na tradução não pode ser recuperada pelo resource pack. Colisões, alcance, lógica, altura do mundo e interfaces permanecem limitados pelo cliente/Via. ViaBackwards documenta restrições de altura para clientes anteriores à 1.17.
- Renderizadores especiais (camas, placas, baús etc.) que mudaram de layout podem continuar com recursos locais. Diferença de proporção de imagem é detectada; igualdade de proporção **não prova** compatibilidade de UV. Inspeção visual continua necessária.
- Algumas texturas podem usar fallback do Prismarine 26.1 se ausentes da 26.3; ficam identificadas no relatório, sem serem apresentadas como recursos 26.3.
- Protocolos anteriores à 1.20.3 não identificam respostas por UUID do pack; deve haver apenas um emissor. 1.7 pode não fornecer todos os estados de carregamento; timeout não prova recusa.
- A API do Via deve enxergar o cliente original no backend. Tradução apenas no proxy exige outra integração, não incluída.

## Compilar e testar

JDK 25 e acesso às dependências Maven:

```text
./gradlew build
```

No Windows: `gradlew.bat build`. O wrapper fixa Gradle 9.2.1. A API Paper usada é `io.papermc.paper:paper-api:26.3.build.245-stable`, compatível com o Paper 26.3 build 245 e superiores.

Testes automatizados cobrem protocolos desconhecidos, seleção exata, configurações inválidas, transições de carregamento, mapeamentos, formatos reais de `version.json`, integridade básica das imagens, referências e geração determinística de ZIP. Relatórios HTML: `core/build/reports/tests/test/` e `generator/build/reports/tests/test/`.

Consulte [configuração](docs/CONFIGURACAO.md), [arquitetura](docs/ARQUITETURA.md) e [validação em clientes](docs/TESTES-MANUAIS.md).

## Fontes

- [PrismarineJS/minecraft-assets](https://github.com/PrismarineJS/minecraft-assets): catálogos, imagens auxiliares e renomeações históricas.
- [Manifesto oficial Minecraft](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json): JARs de cliente, texturas/modelos e metadados de cada versão.
- [ViaVersion/Mappings](https://github.com/ViaVersion/Mappings): registros, substitutos e CustomModelData. Os dados dessa pasta têm permissão expressa de reutilização no README upstream.
- [ViaVersion API](https://github.com/ViaVersion/ViaVersion/wiki/Basic-API-usage) e [Paper API](https://jd.papermc.io/paper/26.3/).

Assets Minecraft continuam pertencendo aos respectivos titulares. O código do projeto não altera os direitos dos assets. Os executáveis do plugin/gerador não incorporam os JARs de cliente.
