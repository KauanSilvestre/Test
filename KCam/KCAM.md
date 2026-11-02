# KCam — fork pessoal do FadCam

KCam é um fork **GPL-3.0** do [FadCam](https://github.com/anonfaded/FadCam) (FadSec Lab).
Não é afiliado nem endossado pela FadSec Lab. Nome provisório — troque em uma linha (veja abaixo).

- Base upstream: `anonfaded/FadCam@e59f58e` (4.0.0-beta10.6 + commits posteriores)
- O commit `Import FadCam upstream source` contém o código original **sem alterações**;
  tudo o que veio depois é do fork. `git diff 1935ae3 -- KCam/` mostra exatamente o que mudou.

## Como instalar no celular

1. Abra **Actions → KCam build** no GitHub e entre na execução mais recente com ✅.
2. Em **Artifacts**, baixe `KCam-debug-<commit>` (zip).
3. Dentro do zip, instale `KCam_v4.0.0-beta10.6-arm64-v8a.apk` (celulares atuais)
   ou `...-universal.apk` se não tiver certeza.
4. O app instala como **KCam Beta** (`io.github.kauansilvestre.kcam.beta`) e **não conflita**
   com o FadCam original.

Para gerar outra edição: Actions → KCam build → *Run workflow* → `edition` = `free` / `pro` / `personal`.

## Configuração (tudo em `gradle.properties`)

| Propriedade | Para quê |
|---|---|
| `kcam.appName` | Nome do app (depois rode `python3 tools/rebrand.py`) |
| `kcam.applicationId` | ID de instalação |
| `kcam.edition` | `personal` (tudo liberado), `free`, `pro` |
| `kcam.updateRepo` | `dono/repo` cujas releases são checadas como atualização (vazio = desliga) |
| `kcam.cloud.*` | Backend de nuvem próprio. **Vazio = nuvem desligada** |

O namespace Java continua `com.fadcam` de propósito: assim dá para puxar melhorias do upstream
sem conflito em centenas de arquivos. O código novo fica em `com.fadcam.kcam.*`.

## O que já foi feito

### Identidade e privacidade
- Nome, ID e edição configuráveis; nome trocado nas strings de todos os idiomas (`tools/rebrand.py`).
- **Sem conexão com servidores da FadSecLab**: nuvem (Supabase/relay/login), checagem de
  atualização, changelog online e notificações do painel remoto agora vêm de configuração.
  A nuvem fica desligada até você ter seu próprio backend; o modo Remote local (Wi-Fi) continua funcionando.
- Classe `Edition` (Personal / Free / Pro) para bloquear recursos por edição.

### Gravação rápida — duplo clique no botão lateral/liga-desliga
Configurações → **Gravação rápida**. Ao acionar, o KCam abre por cima de tudo (inclusive da tela
de bloqueio) e grava conforme a ação escolhida:

- Câmera traseira
- Câmera frontal
- Só áudio
- Tela com áudio
- Tudo: as duas câmeras + a tela. O áudio fica no vídeo das câmeras, porque o Android não deixa
  dois gravadores usarem o microfone ao mesmo tempo.
- Ou "perguntar sempre", que mostra as 5 opções.

Acionar de novo **para tudo**.

Como ligar o duplo clique:

| Celular | Como |
|---|---|
| **Samsung** | Configurações → Recursos avançados → Tecla lateral → Pressionar duas vezes → Abrir app → **KCam Rec** |
| Pixel e outros com "duplo toque no liga/desliga abre a câmera" | escolha **KCam Rec** como câmera quando o sistema perguntar |
| Qualquer celular | ative "Detectar clique duplo no botão liga/desliga (experimental)": o KCam percebe a tela apagando/acendendo duas vezes rápido (precisa de "Sobrepor a outros apps") |
| Qualquer celular | botão "Adicionar botão na tela inicial" |

Nenhum app comum consegue ler o botão liga/desliga diretamente — é uma proteção do Android.
A gravação de tela sempre mostra a confirmação do sistema (exigência do Android 14+).

### Recursos que estavam "em breve" no FadCam e agora funcionam
| Recurso | Onde |
|---|---|
| **FadMic** — gravar só áudio (tela apagada, qualidade voz/padrão/alta, ouvir/compartilhar/apagar) | Tela inicial → botão de modo **FadMic** |
| **Gravação agendada** (horário, dias da semana ou uma vez, duração, câmera) | Configurações → Gravação agendada |
| **Perfis** (salvar as configurações com um nome e trocar com um toque) | Configurações / Avançado / menu lateral → Perfis |
| **Thermal Guardian** (avisa e para a gravação com segurança se o celular esquentar demais) | Configurações → Thermal Guardian |
| **Audio Vision** (começa a gravar sozinho quando detecta som: áudio ou câmera) | Configurações → Audio Vision |
| **FadCrypt** (criptografa gravações com senha, AES-256-GCM) | Avançado → FadCrypt, ou menu da gravação → Criptografar |
| 9 mini apps: bússola, medidor de som, sensores, velocímetro, nível/clinômetro, pedômetro, detector de metais, marcador de estacionamento, gerador de QR | Menu lateral / Configurações → mini apps |
| Botões "Salvar em lote" e "Faditor em lote" | Aba Gravações → selecionar |
| Tela "Pro" → mostra a edição do KCam (em vez da loja do FadCam Pro) | Botão Pro na tela inicial |
| **Português do Brasil** completo (~2.800 textos) | automático em celular em português, ou Aparência → Idioma |

O que **não** entrou, de propósito: FadDrive / FadSec Cloud (é o servidor pago da FadSecLab; no
menu da gravação virou "Criptografar"). Para ter nuvem própria é preciso um servidor seu —
os endereços ficam em `kcam.cloud.*`.

### Build
- `.github/workflows/kcam-build.yml` compila o APK de teste e roda os testes unitários a cada push.
- APK final assinado: veja abaixo.

## APK assinado (versão final, atualiza sem desinstalar)

1. Gere uma chave **uma vez** (no PC com Java, ou no celular com Termux: `pkg install openjdk-17`):
   ```bash
   keytool -genkeypair -v -keystore kcam.jks -alias kcam -keyalg RSA -keysize 4096 -validity 36500
   base64 -w0 kcam.jks > kcam.jks.b64
   ```
   **Guarde `kcam.jks` e as senhas com cuidado**: sem eles você nunca mais consegue atualizar o app.
2. No GitHub: Settings → Secrets and variables → Actions → *New repository secret*:
   `KCAM_KEYSTORE_BASE64` (conteúdo de `kcam.jks.b64`), `KCAM_KEYSTORE_PASSWORD`,
   `KCAM_KEY_ALIAS` (`kcam`), `KCAM_KEY_PASSWORD`.
3. Actions → KCam build → *Run workflow* → marque **release**. Ou crie uma tag `v1.0.0`:
   aí o GitHub também publica uma Release com os APKs.

Saem 4 APKs: o normal e as variantes disfarçadas **Notes**, **Calculator** e **Weather**
(nome e ícone de outro app). Sem os secrets, o job gera APKs "UNSIGNED", que servem só para
conferir se compila.

## Tradução
`python3 tools/i18n_missing.py` lista textos ainda sem português (útil depois de puxar
atualizações do FadCam). As traduções ficam em `app/src/main/res/values-pt/`.

## Como puxar atualizações do FadCam

```bash
git clone https://github.com/anonfaded/FadCam.git /tmp/fadcam-new
cd /tmp/fadcam-new && git diff e59f58e HEAD -- . ':!fastlane' ':!design-assets' > /tmp/upstream.patch
cd <este repo>/KCam && git apply -3 /tmp/upstream.patch
python3 tools/rebrand.py
```
Depois atualize `UPSTREAM_COMMIT` em `app/build.gradle.kts` e o commit base acima.

## Licença

GPL-3.0 (veja `LICENSE`). Se distribuir o APK, distribua também o código-fonte correspondente.
Créditos ao FadCam/FadSec Lab devem ser mantidos.
