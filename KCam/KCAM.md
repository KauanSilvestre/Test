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

### Recursos que estavam "em breve" no FadCam e agora funcionam
| Recurso | Onde |
|---|---|
| Bússola | Menu lateral / Configurações → mini apps |
| Medidor de som (dB) | idem |
| Painel de sensores | idem |
| Velocímetro GPS (km/h ↔ mph, máx, média, distância) | idem |
| Clinômetro / nível de bolha (com zerar) | idem |
| Pedômetro (sessão + hoje) | idem |
| Detector de metais (magnetômetro + vibração) | idem |
| Marcador de estacionamento (seta + distância + abrir no mapa) | idem |
| Gerador de QR (texto/URL, Wi-Fi, contato + compartilhar) | idem |
| **Gravação agendada** (horário, dias da semana ou uma vez, duração, câmera) | Configurações → Gravação agendada |
| Botões "Salvar em lote" e "Faditor em lote" na seleção de gravações | Aba Gravações → selecionar |

Gravação agendada: para iniciar sozinha, conceda **"Sobrepor a outros apps"** e
**"Alarmes e lembretes"** (a tela mostra os botões). Sem isso, chega uma notificação para tocar
e iniciar — é uma restrição do Android para câmera em segundo plano, não do app.

### Build
- `.github/workflows/kcam-build.yml` compila o APK e roda os testes unitários do KCam a cada push.

## Roteiro (o que ainda falta)

Ainda "em breve" no código herdado — ordem sugerida:

1. **Perfis** (salvar/alternar conjuntos de configurações: "carro", "bodycam", "noite").
2. **FadMic** — modo só áudio (gravador de microfone em segundo plano).
3. **Tradução pt-BR** completa (~2.800 strings).
4. **Assinatura de release** própria (keystore nos *Secrets* do GitHub) → APK release estável,
   atualizável sem desinstalar.
5. **Thermal Guardian** (pausar/reduzir qualidade quando o aparelho esquentar).
6. **Audio Vision**, **FadCrypt** (criptografia de vídeos), alertas do Digital Forensics.
7. Nuvem própria (só se fizer sentido: exige servidor, custo e manutenção).
8. Pro: decidir o que é pago. Lembrete da GPL — quem recebe o APK tem direito ao código;
   o valor sustentável está em serviço (nuvem, suporte, builds oficiais), não em esconder código.

Bugs conhecidos do upstream que valem acompanhar: finalização de fMP4 em Android 16/HyperOS
(issue #332 do FadCam) e gravações parando por volta de 5 min em alguns aparelhos.

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
