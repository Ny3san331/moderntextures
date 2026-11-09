# Tutorial de texturas — FixTextures

Este arquivo é copiado para `plugins/FixTextures/TUTORIAL.md` na primeira execução do plugin. Ele explica como trocar texturas, como publicar no GitHub e como o plugin usa o endereço padrão do servidor:

> **Fonte padrão:** <https://ny3san331.github.io/moderntextures/>

O plugin não desenha nada no cliente. Ele apenas escolhe o ZIP certo para a versão do jogador e o entrega por HTTP. Quem muda a aparência é o resource pack.

---

## 1. Como o pack é organizado

Um resource pack é um ZIP com `pack.mcmeta` na **raiz** e a pasta `assets` ao lado:

```text
meu-pack.zip
├── pack.mcmeta
└── assets/
    └── minecraft/
        ├── textures/
        │   ├── block/
        │   ├── item/
        │   ├── entity/
        │   ├── particle/
        │   └── trims/
        ├── models/
        ├── items/
        └── equipment/
```

Regras:

- `pack.mcmeta` **não** pode estar dentro de uma subpasta.
- Não inclua a pasta que o Windows cria ao compactar (`meu-pack/…`); compacte o **conteúdo**.
- Use nomes ASCII, minúsculos, sem espaços e sem acentos.
- Não inclua `__MACOSX`, `.DS_Store` ou arquivos temporários.
- O caminho dentro do ZIP precisa ser exatamente o caminho que o Minecraft espera. Um nome diferente = textura ignorada.

---

## 2. Trocar uma textura existente

Exemplo: mudar a textura de bloco `stone`.

1. Baixe um pack do repositório ou pegue um ZIP em `plugins/FixTextures/packs/`.
2. Abra o ZIP (7-Zip, WinRAR ou o Explorer).
3. Vá até `assets/minecraft/textures/block/`.
4. Substitua `stone.png` pela sua imagem.
   - Mantenha o mesmo nome e o mesmo caminho.
   - Use PNG com transparência quando o original usar.
   - Pode ser 16×16, 32×32, 64×64 etc.; o jogo aceita resoluções maiores.
5. Salve o ZIP **mantendo `pack.mcmeta` na raiz**.

Para item, o caminho costuma ser `assets/minecraft/textures/item/<nome>.png`.
Para entidade, `assets/minecraft/textures/entity/<família>/<arquivo>.png`.

### Animação

Se o PNG original tiver um `.png.mcmeta` ao lado, copie o `.mcmeta` junto. Ele define quadros e tempo. Sem ele, uma textura vertical vira uma imagem esticada.

### Armadura e trims

- Armadura antiga (até 1.20.4): `assets/minecraft/textures/models/armor/<material>_layer_1.png` e `_layer_2.png`.
- Armadura nova (1.21.4+): `assets/minecraft/textures/entity/equipment/humanoid/<material>.png` e `humanoid_leggings/<material>.png`.
- Trims até 1.20.4: `assets/minecraft/textures/trims/models/armor/<padrão>.png` e `<padrão>_leggings.png`.
- Trims 1.21.4+: `assets/minecraft/textures/trims/entity/humanoid/<padrão>.png`.
- Paletas: `textures/trims/color_palettes/` (antigo) ou `textures/palettes/trim/` (26.3).

Não troque `textures/entity/player/` — isso apaga as skins dos jogadores.

---

## 3. Adicionar uma textura nova (variante)

Você pode manter mais de um conjunto de texturas. A ideia:

```text
plugins/FixTextures/
├── config.yml
├── manifest.json          ← variante padrão
├── packs/                 ← zips da variante padrão
├── variants/
│   ├── pvp/
│   │   ├── manifest.json
│   │   └── packs/
│   └── medieval/
│       ├── manifest.json
│       └── packs/
└── TUTORIAL.md
```

No `config.yml`:

```yaml
variants:
  default: default
  allow-player-choice: true
```

No jogo:

```text
/ft texture list
/ft texture pvp
```

Limite: o cliente mantém **um** pack ativo por vez. Trocar de variante reenvia o pack e, em clientes antigos, pode ser necessário reconectar.

Enquanto a versão com variantes não estiver publicada, use o layout atual: um `manifest.json` + os ZIPs em `packs/` ou o bundle `packs.zip`.

---

## 4. Publicar no GitHub (fonte padrão do servidor)

Repositório: <https://github.com/Ny3san331/moderntextures>
Site usado pelos jogadores: <https://ny3san331.github.io/moderntextures/>

### 4.1 Pelo navegador

1. Abra o repositório no GitHub e entre na sua conta.
2. Clique em **Add file → Upload files**.
3. Arraste os arquivos `.zip` e o `manifest.json`.
4. **Não crie subpasta** se o manifesto atual aponta para a raiz. O plugin monta a URL assim:
   `https://ny3san331.github.io/moderntextures/<arquivo-do-manifesto>`.
5. Escreva uma mensagem de commit curta, por exemplo `packs: atualiza texturas`.
6. Clique em **Commit changes**.
7. Aguarde a publicação do GitHub Pages (normalmente 1–3 minutos). Acompanhe em **Actions** ou **Settings → Pages**.
8. Teste no navegador:
   `https://ny3san331.github.io/moderntextures/moderntextures-26.3-to-1.20.4-5288b440abb2.zip`
   Deve baixar o ZIP, não abrir uma página de erro.

### 4.2 Pelo Git (recomendado para muitos arquivos)

```bash
git clone https://github.com/Ny3san331/moderntextures.git
cd moderntextures
# copie os novos ZIPs e o manifest.json para a raiz
git add .
git commit -m "packs: atualiza texturas"
git push origin main
```

### 4.3 Regras de nome

- Tudo minúsculo, ASCII, sem espaço.
- Extensão `.zip`.
- Não renomeie um pack que já está publicado sem atualizar o `manifest.json`; jogadores podem estar baixando o antigo.
- O nome gerado pelo ModernTextures segue o padrão
  `moderntextures-26.3-to-<versão>-<12 primeiros do sha1>.zip`.
  **Mantenha esse prefixo** para não quebrar URLs e manifests existentes.

### 4.4 Limites do GitHub Pages

- Arquivo individual: até 100 MB (o GitHub avisa acima de 50 MB).
- Repositório: mantenha abaixo de ~1 GB.
- Banda: o Pages não é CDN; para muitos jogadores simultâneos, use Cloudflare/CDN na frente.
- Não há autenticação: qualquer pessoa com a URL baixa o pack.

---

## 5. Atualizar o manifesto

O `manifest.json` diz ao plugin qual ZIP pertence a cada protocolo e qual é o SHA-1. Exemplo:

```json
{
  "schema": 1,
  "sourceVersion": "26.3",
  "packs": [
    {
      "clientVersion": "1.20.4",
      "protocol": 765,
      "file": "moderntextures-26.3-to-1.20.4-5288b440abb2.zip",
      "sha1": "5288b440abb2..."
    }
  ]
}
```

Se você trocou um ZIP, o SHA-1 muda. Opções:

1. **Recomendado:** rodar o gerador, que calcula hash, nome e manifesto:
   ```text
   java -jar ModernTextures-Generator.jar --targets 1.20.4 --config mappings.json --output packs --cache cache
   ```
2. Manual (Windows PowerShell):
   ```powershell
   (Get-FileHash 'meu-pack.zip' -Algorithm SHA1).Hash.ToLower()
   ```
   Copie o hash para o campo `sha1` e ajuste `file`.

Depois:

```text
/ft reload
```

O plugin valida o manifesto. Se estiver inválido, mantém a configuração anterior e mostra o erro.

---

## 6. Testar antes de publicar

1. Coloque o ZIP em `plugins/FixTextures/packs/` (ou no `packs.zip`).
2. Aponte `base-url` para o GitHub Pages **ou** ligue o servidor embutido.
3. Entre com um cliente da versão alvo.
4. Rode `/ft send SeuNome`.
5. Confira:
   - o pack aparece na lista de packs do cliente?
   - o download conclui?
   - o estado final é `SUCCESSFULLY_LOADED`?
   - a textura mudou no inventário, no mundo e na mão?

`ACCEPTED` e `DOWNLOADED` não significam aplicado. Só `SUCCESSFULLY_LOADED` confirma carregamento.

---

## 7. Problemas comuns

| Sintoma | Causa provável | Correção |
|---|---|---|
| Pack aparece como inválido | `pack.mcmeta` dentro de subpasta ou JSON quebrado | recompactar com `pack.mcmeta` na raiz |
| Textura não muda | caminho/nome diferente do original | conferir caminho exato dentro do ZIP |
| Textura esticada | `.mcmeta` de animação ausente | copiar o `.mcmeta` junto |
| Download 404 | arquivo não enviado ao GitHub ou nome diferente do manifesto | conferir URL direta no navegador |
| Hash divergente | ZIP alterado depois do manifesto | recalcular SHA-1 |
| Jogador antigo não vê | item/bloco/entidade não existe na versão dele | limitação do cliente; Via não cria conteúdo novo |
| Lança parece espada | modelo antigo sem pose de `_in_hand` | usar o pack novo com modelo de lança |
| Skins dos jogadores sumiram | pack incluiu `textures/entity/player/` | remover essa pasta |

---

## 8. Boas práticas

- Guarde uma cópia do pack antes de editar.
- Uma alteração por commit; fica fácil voltar.
- Teste em pelo menos uma versão antiga e uma nova.
- Não publique conteúdo que você não tem direito de usar.
- Mantenha os créditos dos assets e das fontes no README.
- Não apague packs antigos enquanto houver jogadores com cache apontando para eles.

## 9. Links

- Repositório: <https://github.com/Ny3san331/moderntextures>
- Site dos packs: <https://ny3san331.github.io/moderntextures/>
- MC Assets (consulta de texturas): <https://mcasset.cloud/>
- Manifesto oficial: <https://piston-meta.mojang.com/mc/game/version_manifest_v2.json>
