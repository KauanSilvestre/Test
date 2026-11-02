# PROMPT MESTRE — CAIXA PRETA
### App Android de gravação de prova pessoal ("sobrevivência digital")

> **Como usar:** cole este documento inteiro como primeira mensagem para um agente de IA de programação
> (Claude Code ou similar) com acesso a um repositório Git e ao GitHub Actions. O agente deve ler tudo antes
> de escrever qualquer código e seguir as fases da seção 18. Este texto é a especificação oficial do produto.

---

## 0. Seu papel e sua forma de trabalhar

Você é um **engenheiro Android sênior** especializado em:
- captura de mídia (Camera2/CameraX, MediaCodec, áudio de baixa latência, MediaProjection);
- serviços em primeiro plano e restrições de segundo plano das versões 10 a 16 do Android;
- segurança aplicada (Android Keystore, criptografia autenticada, cadeia de custódia de prova digital);
- design de interface para situações de estresse.

Você vai construir **do zero ao APK instalável** o app descrito aqui.

Regras de trabalho, não negociáveis:

1. **Leia o documento inteiro antes de começar.** Depois escreva um plano curto (arquivo `docs/PLANO.md`) e siga as fases da seção 18 em ordem.
2. **Nada de resultado inventado.** Se não compilou, diga que não compilou e mostre o erro. Se não testou em aparelho real, diga que não testou. Nunca escreva "funciona" sem evidência: log de build, teste passando, `ffprobe` de um arquivo gravado, print.
3. **Compile de verdade.** Se o seu ambiente não tiver Android SDK ou bloquear o Google Maven (`dl.google.com`), **não pare**: crie o workflow do GitHub Actions da seção 17 no primeiro commit e use o CI como compilador. Leia os logs pela API/ferramentas do GitHub e corrija até ficar verde.
4. **Commits pequenos e descritivos**, um assunto por commit, sempre com o build verde antes do próximo passo grande.
5. **Restrições do Android são requisitos, não obstáculos para "dar um jeito".** Nunca tente burlar indicadores de privacidade do sistema, telas de consentimento ou proteções de segurança. Onde algo é impossível, implemente a melhor alternativa legítima e **documente o limite** na tela de Prontidão e no README.
6. **Pergunte ao usuário só o que é decisão dele** (nome final, destino de upload, contatos de emergência). Todo o resto: decida com base neste documento e registre a decisão em `docs/DECISOES.md`.
7. Escreva **toda a interface em português do Brasil** (pt-BR), com inglês como segundo idioma. Use `strings.xml` de verdade e escape apóstrofos (`\'`) **inclusive dentro de CDATA**: um único apóstrofo sem escape derruba a compilação de todos os recursos.

---

## 1. A missão em uma frase

> **Dois cliques no botão do celular: tudo começa a ser gravado na hora, com o celular bloqueado ou desbloqueado, sem chamar atenção. A gravação vira prova que sobrevive mesmo se tomarem ou destruírem o aparelho.**

Lema do produto: **"Dois cliques. Tudo fica registrado."**

Princípios, em ordem de prioridade:
1. **Começar a gravar é instantâneo e infalível.** Mais rápido que qualquer outra coisa no app. Nenhuma tela de confirmação, nenhuma contagem regressiva, nenhum menu.
2. **A prova sobrevive.** Arquivos resistentes a travamento, cópia fora do aparelho, integridade verificável.
3. **Discrição.** Quem está ao redor não deve perceber que o app foi acionado.
4. **Honestidade técnica.** O app diz exatamente o que está e o que não está gravando.
5. Todo o resto é secundário. O app faz **uma** coisa e a faz extremamente bem.

---

## 2. De onde veio a inspiração

| Fonte | O que aproveitamos |
|---|---|
| **FadCam** ([github.com/anonfaded/FadCam](https://github.com/anonfaded/FadCam), FadSec Lab, GPL-3.0) | Gravação com tela apagada via serviço em primeiro plano. MP4 **fragmentado** (fMP4) que sobrevive a travamento. Câmera dupla em PiP. Gravador de tela (FadRec). Atalhos que iniciam gravação sem abrir o app. Foi a base do protótipo **KCam** (fork em `github.com/KauanSilvestre/Test`, pasta `KCam/`), que provou o conceito de "Gravação rápida". |
| **Gravador e câmera da Samsung** | O gesto de **pressionar duas vezes a tecla lateral** para abrir algo instantaneamente. Queremos o mesmo reflexo: dois cliques e está gravando. |
| **Caixa-preta de avião** (FDR/CVR) | Grava tudo continuamente, é feita para **sobreviver ao acidente** e ninguém a desliga no meio do voo. Detalhe: caixas-pretas reais são **laranja** ("laranja internacional"), para serem encontradas nos destroços. Essa é a origem do nome e da cor de marca. |
| **eyeWitness to Atrocities** (International Bar Association) e **ProofMode** (Guardian Project) | Prova digital com **metadados verificáveis**, hashes, assinatura criptográfica e **cadeia de custódia**: uma gravação que um tribunal pode levar a sério. |
| **Mobile Justice** (ACLU) | Envio imediato da gravação para fora do aparelho: se o celular for tomado, a prova já saiu. |

---

## 3. Para que serve, e para que não serve

### 3.1 Cenários reais
- Abordagem policial abusiva.
- Assalto ou ameaça na rua.
- Violência doméstica e ameaças recorrentes.
- Assédio no trabalho ou no transporte.
- Briga de trânsito ou acidente.
- Discussão em que você precisa provar o que foi dito.

Em todos esses cenários **o dono do celular está presente e participa da situação**, e precisa de prova sem poder parar para mexer no telefone.

### 3.2 Base legal no Brasil (para orientar o produto, não é aconselhamento jurídico)
- **STF, RE 583.937 (Tema 237 de repercussão geral):** é lícita a prova consistente em gravação ambiental feita **por um dos interlocutores** sem o conhecimento do outro.
- **Lei 9.296/1996, art. 8-A, §4º** (incluído pela Lei 13.964/2019): a captação ambiental feita por um dos interlocutores pode ser usada **em matéria de defesa, quando demonstrada a integridade da gravação**. Por isso a seção 10 (integridade e cadeia de custódia) é núcleo do produto, não um extra.
- Mostre esse resumo dentro do app (tela "Sobre a prova"), com a recomendação de procurar um advogado ou a Defensoria Pública.

### 3.3 O que o app NÃO é (regras de produto)
- **Não é ferramenta de espionagem.** Não existe ativação remota por terceiros, nem gravação disparada por outra pessoa, nem envio de gravação para ninguém além dos destinos que o próprio dono configurou.
- **Não se esconde do dono do aparelho.** Discrição é contra um agressor que está olhando a tela, não contra o dono.
- **Não tenta burlar** os indicadores de câmera e microfone do Android (bolinha verde), o consentimento de gravação de tela ou a tela de bloqueio.
- **Não apaga prova automaticamente.** Nunca.

---

## 4. Nome e identidade

**Nome: Caixa Preta.** O app grava tudo, sobrevive ao "acidente" e é a prova que sobra depois. O nome se explica sozinho em português.

- `applicationId`: `io.github.kauansilvestre.caixapreta`
- Nome curto na tela inicial: **Caixa Preta**
- Atalho de disparo, que é uma entrada separada no launcher, para escolher na tecla lateral da Samsung: **"Caixa Preta ●"**
- Alternativas registradas, caso o usuário prefira: *Testemunha*, *Álibi*, *Vigília*, *Sentinela*.
- **Disfarce opcional** (seção 12.4): ícone e nome neutros, como "Notas" ou "Calculadora". Desligado por padrão.

---

## 5. Requisitos funcionais

### P0: núcleo (sem isso, o app não existe)

| ID | Requisito |
|---|---|
| F1 | **Disparo por dois cliques** no botão físico. Implementar todos os gatilhos da seção 7. |
| F2 | **Gravar tudo ao mesmo tempo**: câmera traseira, câmera frontal, microfone(s), tela (quando permitido), localização GPS e metadados. Tudo numa **sessão**. |
| F3 | Funciona com o celular **bloqueado e desbloqueado**, tela ligada ou recém-apagada. |
| F4 | **Latência**: áudio gravando em ≤ 700 ms e primeira imagem da câmera traseira em ≤ 1,5 s (desbloqueado) ou ≤ 2 s (bloqueado), contados a partir do disparo. |
| F5 | **Sem interface de confirmação.** Depois do disparo: tela totalmente preta por no máximo 2,5 s, depois volta exatamente para onde estava (tela de bloqueio, ou o app que estava aberto). |
| F6 | **Disparar de novo durante a gravação NÃO para a gravação.** Ele cria um **marcador** ("momento importante") com vibração de confirmação. Parar exige autenticação (biometria, PIN ou padrão do aparelho). |
| F7 | **Resistente a travamento**: matar o app, acabar a bateria ou desligar à força perde no máximo ~1 s de cada fluxo. Os arquivos parciais tocam em qualquer player. |
| F8 | **Degradação graciosa**: se uma fonte falhar (câmera ocupada, ligação telefônica no microfone, superaquecimento), as outras continuam. A falha fica registrada na sessão. |
| F9 | **Integridade**: hash SHA-256 de cada segmento, cadeia de hashes da sessão e assinatura com chave em hardware (Android Keystore). Exportação com verificador. |
| F10 | **Cofre de provas**: lista de sessões com reprodução sincronizada, mapa, marcadores, verificação de integridade, exportação e compartilhamento. Abrir o cofre exige autenticação. |
| F11 | **Tela de Prontidão**: checklist que prova que o app vai funcionar na hora H (seção 13.5). |

### P1: a prova sai do aparelho
| ID | Requisito |
|---|---|
| F12 | **Envio quase em tempo real** dos segmentos, criptografados, para um destino do próprio usuário (seção 11.3). Ordem de prioridade: manifesto → localização → áudio → fotos-chave → vídeo. |
| F13 | **Alerta opcional para contatos de confiança** por SMS, com link de mapa, horário e bateria. Desligado por padrão. |
| F14 | **Fotos-chave**: um JPEG de baixa resolução de cada câmera a cada 2 s, enviado antes do vídeo, porque é prova leve que sai mesmo em rede ruim. |
| F15 | **Autoteste**: botão "Testar agora" que grava 5 s de tudo, valida os arquivos e mostra o resultado. |
| F16 | **Disfarce** de ícone e nome (opcional). |

### P2: depois
- Relógio Wear OS como gatilho.
- Credenciais de conteúdo **C2PA** nos vídeos.
- Carimbo de tempo **RFC 3161** (TSA) quando houver rede.
- Gatilho por controle Bluetooth.

---

## 6. Realidades da plataforma Android (respeite, contorne com legitimidade, documente)

| Restrição | Consequência | Como tratar |
|---|---|---|
| Nenhum app comum lê o **botão liga/desliga** | Não dá para "escutar" o power diretamente | Use os gatilhos da seção 7: tecla lateral Samsung → abrir app, gesto do sistema "duplo toque abre câmera", detector de liga/desliga de tela, bloco de Configurações Rápidas, atalho. |
| **Câmera e microfone em segundo plano** (Android 11+, regras de "enquanto em uso") | Um serviço só pode usar câmera e mic se foi iniciado com o app **visível** ou com uma isenção | O gatilho sempre abre uma **Activity visível** (`TriggerActivity`, preta, `showWhenLocked`), que inicia o serviço com tipos `camera\|microphone\|location`. Ela só fecha depois que o serviço confirmar a gravação, com tempo-limite de 2,5 s. |
| **Tela de bloqueio** | Activities comuns não aparecem por cima do bloqueio | `setShowWhenLocked(true)` / `android:showWhenLocked="true"`. **Nunca** chame `requestDismissKeyguard()` no disparo, porque isso mostraria o teclado de PIN para o agressor. |
| **Gravação de tela exige consentimento** a cada sessão (Android 14+; o token de `MediaProjection` é de uso único) | Tela **não** começa sem um toque do usuário no diálogo do sistema, e o diálogo **não aparece sobre o bloqueio seguro** | Câmeras e áudio começam **primeiro**, na hora. A tela entra em seguida, **só se o aparelho estiver desbloqueado**, mostrando o diálogo do sistema (com a opção "Tela inteira" pré-selecionada via `MediaProjectionConfig.createConfigForDefaultDisplay()` no API 34+). Com o aparelho bloqueado: pule a tela e registre "tela: indisponível (bloqueado)". Exponha a opção "Incluir tela" (padrão: ligada quando desbloqueado). |
| **Câmeras simultâneas** só em parte dos aparelhos (`CameraManager.getConcurrentCameraIds()`, CameraX `getAvailableConcurrentCameraInfos()`) | Alguns celulares não abrem frontal e traseira juntas | Detecte na instalação. Se suportar: dois fluxos. Se não: grave só a **câmera prioritária** (traseira por padrão, configurável), registre "frontal: não suportada neste aparelho" e mostre isso na Prontidão. |
| **Concorrência de microfone** e **ligações** | Durante uma ligação o app pode receber silêncio | Capture o mic **uma única vez** (seção 9.3). Monitore `AudioManager.AudioRecordingCallback` com `AudioRecordingConfiguration.isClientSilenced()` (API 29+) e marque trechos silenciados na sessão. |
| **Indicadores de privacidade** (Android 12+) | A bolinha verde aparece sempre | Não é contornável. Documente. |
| **Notificação obrigatória** de serviço em primeiro plano | Algo sempre aparece na gaveta | Canal discreto, sem som, com `VISIBILITY_SECRET` (não aparece na tela de bloqueio segura) e texto neutro configurável. Detalhes na seção 12. |
| **Fabricantes que matam apps** (Samsung, Xiaomi e outros) | O serviço morre ou o disparo falha | Prontidão: pedir isenção de otimização de bateria (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) e guiar o usuário por "Apps que nunca hibernam" (Samsung). Referência: dontkillmyapp.com. |
| **Início de Activity em segundo plano** (BAL, cada vez mais restrito no 14/15) | O detector de tela não consegue abrir a TriggerActivity | Exige a permissão "Sobrepor a outros apps" (`SYSTEM_ALERT_WINDOW`) como isenção. Em `PendingIntent`s criados pelo app, use opt-in explícito de BAL. |
| **Android 15**: `BOOT_COMPLETED` não pode iniciar serviço de câmera ou microfone | Não dá para retomar gravação após reiniciar | Após reboot, só finalize e recupere a sessão interrompida, e reative o detector de gatilho (`specialUse`). |
| **Configurações restritas** (Android 13+) para apps instalados fora da loja | Ativar Acessibilidade fica bloqueado até "Permitir configurações restritas" | Se usar o gatilho por Acessibilidade (opcional), guie o usuário por Informações do app → ⋮ → Permitir configurações restritas. |
| **Páginas de 16 KB** (aparelhos Android 15+) | Bibliotecas nativas desalinhadas quebram | Evite libs nativas. Se usar, alinhe em 16 KB e verifique com `zipalign -c -P 16`. |
| **Edge-to-edge obrigatório** (targetSdk 35+/36) | A interface fica sob as barras | Trate os insets em todas as telas Compose. |

---

## 7. Gatilhos (como os "dois cliques" viram gravação)

Todos os gatilhos terminam no mesmo ponto: `TriggerActivity` → `CaptureService.start(source = <gatilho>)`.

### 7.1 TriggerActivity (o coração do disparo)
- **Tema**: preto opaco, sem barras, sem animação de entrada (`overridePendingTransition(0,0)` ou equivalente no API 34+ com `overrideActivityTransition`), `excludeFromRecents`, `taskAffinity` próprio, `launchMode="singleTask"`, `showWhenLocked=true`, **`turnScreenOn=false`**. Não acende a tela se ela estava apagada; quem acende é o gesto físico, quando acende.
- **Fluxo em `onCreate`**, nesta ordem e sem esperar a interface:
  1. Se já está gravando: `CaptureService.mark(source)`, vibração de "marcador", `finish()`.
  2. Debounce: ignore disparos a menos de 1,5 s do último.
  3. Se faltar permissão crítica (câmera ou mic): mesmo assim inicie o que for possível (por exemplo, só localização) e registre o erro. **Nunca** mostre diálogo de permissão no disparo; a Prontidão garante as permissões antes.
  4. `ContextCompat.startForegroundService(CaptureService.START)`.
  5. Observe o estado do serviço (bind local ou `StateFlow` em singleton de processo) até `RECORDING`, ou 2,5 s.
  6. Se desbloqueado e "Incluir tela" estiver ligado: lance o consentimento de `MediaProjection` (seção 9.4) **depois** que câmeras e áudio já estão gravando.
  7. `finishAndRemoveTask()`. O usuário volta para a tela de bloqueio ou para o app anterior.
- Telemetria local: grave no log da sessão o horário de cada etapa (disparo → serviço criado → áudio → vídeo), para medir a latência F4.

### 7.2 Samsung: "Pressionar duas vezes a tecla lateral → Abrir app" (gatilho principal)
- Entrada de launcher separada via `activity-alias` (`MAIN`/`LAUNCHER`) chamada **"Caixa Preta ●"**, apontando para a `TriggerActivity`. É ela que o usuário escolhe em *Configurações → Recursos avançados → Tecla lateral → Pressionar duas vezes → Abrir app*.
- **Fase 0 obrigatória**: verificar num Samsung real se, com o aparelho **bloqueado**, a One UI abre a alias diretamente (por cima do bloqueio, graças ao `showWhenLocked`) ou pede desbloqueio. Documente o resultado por versão da One UI. Se pedir desbloqueio, a Prontidão recomenda **também** o gatilho 7.4.

### 7.3 Gesto do sistema "pressionar o liga/desliga duas vezes abre a câmera" (Pixel e outros)
- Declare na `TriggerActivity` os filtros `android.media.action.STILL_IMAGE_CAMERA` e `android.media.action.STILL_IMAGE_CAMERA_SECURE` (categoria `DEFAULT`). O usuário escolhe o Caixa Preta como câmera do gesto ou como câmera padrão.
- Explique o efeito colateral na Prontidão: outros apps que pedirem "abrir câmera" vão abrir o Caixa Preta.

### 7.4 Detector de liga/desliga (qualquer aparelho, opcional)
- `PowerButtonWatchService`: serviço em primeiro plano tipo `specialUse` (com a `property` `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` justificando), notificação mínima e receiver dinâmico de `ACTION_SCREEN_ON`/`ACTION_SCREEN_OFF`.
- Regra: **duas transições de tela em ≤ 700 ms** = disparo, com cooldown de 2,5 s. Ajuste com dados reais e permita calibrar a janela (500–900 ms).
- Requer "Sobrepor a outros apps" (isenção de BAL) para abrir a `TriggerActivity` em segundo plano. Sem ela, o detector fica desativado com aviso.
- Religar após boot e atualização do app (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`). O tipo `specialUse` é permitido no boot.

### 7.5 Outros gatilhos
- **Bloco de Configurações Rápidas** (`TileService`): um toque inicia. Valide o comportamento no bloqueio (em aparelho seguro pode exigir desbloqueio; documente).
- **Atalho fixado** na tela inicial e **widget** 1x1 "Gravar".
- **Notificação persistente opcional** "Pronto" com ação de disparo (ações de notificação podem iniciar Activity).
- **Acessibilidade (opcional, P1)**: segurar volume-baixo por 2 s com a tela ligada. Exige o fluxo de "configurações restritas"; deixe claro que não funciona com a tela apagada.
- **Wear OS** (P2).

### 7.6 Comportamento padrão quando o gatilho dispara
- Fontes: todas as disponíveis.
- Vibração: código "início" (seção 12.2), configurável ou desativável.
- Nenhum som, nenhum toast, nenhum banner.

---

## 8. Arquitetura

### 8.1 Stack
- **Kotlin** 2.x, Coroutines/Flow e **Jetpack Compose** com Material 3 e tema totalmente customizado (nada de roxo padrão).
- `minSdk 29` (Android 10), `targetSdk`/`compileSdk` = o mais recente estável (36 ou superior). Use AGP, Gradle, CameraX, Media3, Room, DataStore e WorkManager nas **últimas versões estáveis**, fixadas em `gradle/libs.versions.toml`, e confira o changelog de cada uma.
- **Sem Google Play Services obrigatório** (o app precisa funcionar em qualquer Android): use `LocationManager`, não Fused.
- Injeção de dependência: Hilt, ou manual se ficar mais simples. Log: Timber com árvore própria que grava um log local rotativo e exportável, **sem dados sensíveis**.

### 8.2 Módulos
```
:app                  — Activities, navegação, tema, DI
:core:capture         — CaptureService, máquina de estados, orquestração
:core:camera          — câmeras (concorrentes ou única), renderizador GL, fotos-chave
:core:audio           — captura única de áudio, AAC, fan-out
:core:screen          — MediaProjection, VirtualDisplay, captura de áudio interno (opcional)
:core:media           — encoders MediaCodec, muxer fMP4, segmentador, relógio comum
:core:evidence        — hashes, cadeia, assinatura Keystore, manifesto, exportação
:core:storage         — layout de arquivos, criptografia em repouso, Room (sessões/segmentos)
:core:upload          — fila, destinos (Telegram, WebDAV, S3), criptografia de envio
:core:triggers        — TriggerActivity, alias, tile, detector, atalhos
:core:alerts          — SMS de emergência (P1)
:feature:dashboard  :feature:readiness  :feature:vault  :feature:settings  :feature:onboarding
tools/verify          — verificador de integridade em Python (fora do APK)
```

### 8.3 Máquina de estados da sessão
```
IDLE → ARMING → RECORDING ⇄ DEGRADED → FINALIZING → SEALED
                     ↘ (crash/kill) → INTERRUPTED → (próxima abertura) RECOVERED → SEALED
```
- `ARMING`: abre áudio primeiro (mais rápido), depois as câmeras em paralelo, depois localização.
- `DEGRADED`: alguma fonte caiu e está em retentativa com backoff (1 s, 2 s, 4 s… teto 30 s).
- `FINALIZING`: fecha os segmentos, faz `fsync`, calcula os hashes finais, assina o manifesto e enfileira o upload.
- `SEALED`: imutável. Qualquer alteração posterior é detectável.

### 8.4 CaptureService
- `LifecycleService` (o CameraX exige um `LifecycleOwner`), em processo **separado** (`android:process=":capture"`), para que um crash da interface não derrube a gravação.
- `foregroundServiceType="camera|microphone|location"` no início. Ao receber o consentimento de tela, chame `startForeground` de novo **adicionando** `mediaProjection` (API 29+ permite atualizar os tipos).
- `PARTIAL_WAKE_LOCK` durante a sessão.
- Estado exposto por `StateFlow` (`SessionState`: fontes ativas, tempos, bytes, erros), acessível por binder local e por `ContentProvider`/`Messenger` para a interface no outro processo.
- **Watchdog interno**: se um encoder não emite saída por 3 s, reinicia aquele fluxo e registra o evento.

### 8.5 Modelo de dados (Room)
`Session(id, startedAtUtc, startedElapsedNs, trigger, deviceInfo, locked, state, sealedAt, chainHead, signature)`
`Stream(id, sessionId, kind[BACK|FRONT|SCREEN|AUDIO|SNAPSHOTS|LOCATION], codec, w, h, fps, status, failReason)`
`Segment(id, streamId, index, path, bytes, sha256, startPtsUs, endPtsUs, encrypted, uploadedAt)`
`Marker(id, sessionId, elapsedNs, source)` · `LocationFix(sessionId, timeUtc, lat, lon, acc, speed, bearing, provider)`
`UploadTask(segmentId, destination, attempts, nextAttemptAt, state)` · `EventLog(sessionId, elapsedNs, level, code, message)`

### 8.6 Arquivos
Tudo em **armazenamento interno do app** (`filesDir/sessions/`): não aparece na galeria, em gerenciadores de arquivo nem no cabo USB.
```
sessions/2026-11-03T21-14-07Z_7f3a/
  manifest.json          (assinado no SEALED; parcial atualizado a cada segmento)
  back/000.mp4 001.mp4 …  front/…  screen/…  audio/000.m4a …
  snapshots/back_000123.jpg …
  location.jsonl  markers.jsonl  events.jsonl
```

---

## 9. Pipeline de mídia (detalhado)

### 9.1 Relógio comum
Todas as fontes usam `SystemClock.elapsedRealtimeNanos()` como base. O PTS de cada amostra é relativo ao `startedElapsedNs` da sessão. O manifesto guarda o par (horário UTC do sistema, elapsed) no início, e o horário GNSS quando houver fix. Assim os fluxos podem ser alinhados na reprodução e na perícia.

### 9.2 Câmeras
- **Concorrentes suportadas** → CameraX `bindToLifecycle(listOf(SingleCameraConfig(traseira, ...), SingleCameraConfig(frontal, ...)))`. Cada câmera usa `Preview` com `SurfaceProvider` apontando para a **entrada de um renderizador OpenGL ES 3** próprio.
- **Renderizador GL por câmera** (o "trilho central"):
  1. Recebe o `SurfaceTexture` da câmera.
  2. Desenha no **Surface de entrada do encoder** (`MediaCodec.createInputSurface()`).
  3. Desenha num `ImageReader` de baixa resolução para as **fotos-chave** (a cada 2 s, JPEG ~640 px, qualidade 70).
  4. Opcional: grava na imagem um **carimbo de data/hora UTC + coordenadas**, como prova visual.
  5. Fallback: se o aparelho não suportar dois encoders simultâneos (`CodecCapabilities.getMaxSupportedInstances()` e testes reais), **compõe as duas câmeras num único quadro PiP** e usa um encoder só.
- Sem câmeras concorrentes → só a câmera prioritária. Registre a outra como indisponível.
- **Reconexão**: tratar `onDisconnected` e `ERROR_CAMERA_IN_USE` (outro app pegou a câmera), mantendo o áudio, com retentativa em backoff. Durante a falha, o vídeo daquele fluxo **pausa** (não grava quadro preto falso), e a lacuna fica marcada no manifesto.
- Ajustes: estabilização ligada quando disponível, AE/AF contínuos, e modo de pouca luz (`CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY`, API 35) quando suportado e permitido.

### 9.3 Áudio: capturar UMA vez, gravar em todo lugar
- **Um único `AudioRecord`**:
  - Fonte: `CAMCORDER` (costuma usar 2 microfones/estéreo). Se `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` existir, ofereça `UNPROCESSED` (som cru, melhor para perícia). Fallback: `MIC`.
  - 48 kHz, estéreo se suportado (`AudioRecord` com `CHANNEL_IN_STEREO`).
  - Liste `AudioManager.getMicrophones()` e registre no manifesto quais microfones o sistema reporta. "Todos os microfones" = **todos os canais que o Android expõe para a fonte escolhida**; não prometa mais que isso.
- **Um único encoder AAC-LC** (128 kbps estéreo), cuja saída codificada é escrita em:
  - a trilha de áudio de **cada** arquivo de vídeo (traseira, frontal, tela);
  - um arquivo **só de áudio** independente (`audio/NNN.m4a`), que é a redundância e o primeiro a ser enviado.
- Detecte silêncio imposto pelo sistema (`isClientSilenced`), ligação ativa (`AudioManager.mode`) e troca de dispositivo (fone com fio/Bluetooth). Registre os eventos.

### 9.4 Tela
- Consentimento via `createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())` (API 34+), lançado pela TriggerActivity **depois** que câmeras e áudio já gravam.
- No API 34+: `registerCallback` **antes** de `createVirtualDisplay`, e um único `VirtualDisplay` por projeção.
- Resolução: lado maior ≤ 1280, 15–24 fps, H.264 2,5 Mbps, com o mesmo áudio do mic (9.3) e, opcionalmente, uma **segunda trilha** com o áudio interno (`AudioPlaybackCaptureConfiguration`).
- Fim da projeção (usuário tocou em "Parar compartilhamento" ou bloqueou): fecha o fluxo de tela e **mantém o resto**.

### 9.5 Encoders e contêiner
- Vídeo: H.264 High (HEVC opcional se o aparelho suportar bem), VBR, intervalo de I-frame de 1 s, `KEY_PRIORITY` realtime.
- **Contêiner: MP4 fragmentado** (Media3 `FragmentedMp4Muxer`, fragmentos de **1 s**).
  - Atenção: o FadCam precisou de um Media3 com patch (repositório `anonfaded/media3-patched`) por um bug de `stsz`/`trun`. Teste a versão oficial atual com o roteiro de verificação da seção 16. Se falhar, use o patch ou um writer fMP4 próprio mínimo.
- **Segmentação**: um novo arquivo a cada **30 s** (configurável de 10 a 120 s) ou 50 MB. Começa num I-frame e é fechado com `FileChannel.force(true)`.
- **Escada de qualidade** (descer um degrau por gatilho de temperatura, armazenamento ou falha de encoder):

| Degrau | Traseira | Frontal | Tela | Áudio |
|---|---|---|---|---|
| A (padrão) | 1080p30 6 Mbps | 720p30 3 Mbps | 720p20 2,5 Mbps | AAC 128k estéreo |
| B | 720p30 3,5 Mbps | 480p30 1,5 Mbps | 720p15 1,5 Mbps | igual |
| C | 480p24 1,5 Mbps | 360p15 0,6 Mbps | desligada | igual |
| D (sobrevivência) | só fotos-chave | só fotos-chave | desligada | AAC 64k mono |

### 9.6 Políticas de proteção
- **Temperatura**: `PowerManager.getThermalHeadroom()` e `addThermalStatusListener`. Em `SEVERE`, desce um degrau; em `CRITICAL`, vai para o D. Nunca para totalmente por conta própria.
- **Armazenamento**: livre < 1 GB → degrau C; < 300 MB → D; < 50 MB → finaliza com segurança. **Nunca apaga sessões antigas sozinho.**
- **Bateria**: ≤ 5% → D; ≤ 2% → finaliza e sela (prova íntegra vale mais que 30 s a mais).
- **Duração máxima**: nenhuma por padrão (configurável).

---

## 10. Integridade e cadeia de custódia

1. **Hash por segmento**: SHA-256 do arquivo fechado (do texto puro, antes de criptografar).
2. **Cadeia**: `H_0 = SHA256("CAIXAPRETA-v1" ‖ sessionId ‖ startedAtUtc ‖ deviceKeyFingerprint)` e depois `H_n = SHA256(H_{n-1} ‖ stream ‖ index ‖ sha256(segmento) ‖ startPts ‖ endPts)`, em ordem global de fechamento. Isso detecta remoção, troca ou reordenação de qualquer segmento.
3. **Chave em hardware**: um par ECDSA P-256 gerado no Android Keystore na primeira execução.
   - StrongBox quando disponível (`setIsStrongBoxBacked`), sem exportação possível.
   - **Atestação de chave** (`setAttestationChallenge`): a cadeia de certificados prova que a chave nasceu no hardware deste aparelho.
   - Assine `chainHead` e o `manifest.json` canônico no `SEALED`. Assine também o head parcial a cada 60 s, para que uma sessão interrompida tenha uma assinatura recente.
4. **Manifesto** (JSON canônico: chaves ordenadas, UTF-8, sem espaços), por exemplo:
```json
{
  "format":"caixapreta/1","sessionId":"7f3a…","trigger":"samsung_side_key","deviceLocked":true,
  "startedAtUtc":"2026-11-03T21:14:07.412Z","startedElapsedNs":123456789000,
  "clock":{"gnssUtc":"2026-11-03T21:14:08Z","offsetMs":-412},
  "device":{"model":"SM-A556E","android":"16","app":"1.0.0(10)","keyFingerprint":"sha256:…"},
  "streams":[{"kind":"BACK","codec":"avc","w":1920,"h":1080,"fps":30,
     "segments":[{"i":0,"sha256":"…","bytes":22811234,"ptsUs":[0,30000000]}]}],
  "gaps":[{"stream":"FRONT","fromNs":…,"toNs":…,"reason":"CAMERA_IN_USE"}],
  "markers":[{"elapsedNs":…,"source":"side_key"}],
  "chainHead":"…","signature":"base64(ECDSA)","attestationChain":["base64(cert)", "…"]
}
```
5. **Exportação para perícia**: um `.zip` com as mídias em texto puro, o `manifest.json`, `location.geojson`, os `events.jsonl`, a cadeia de certificados e um `LEIA-ME.txt` em pt-BR explicando como verificar.
6. **Verificador** `tools/verify/caixapreta_verify.py` (Python 3, sem dependências exóticas): recalcula hashes e cadeia, valida a assinatura ECDSA e a cadeia de atestação e imprime ✅/❌ por item. Inclua testes com um arquivo adulterado (1 byte) que precisa falhar.
7. **Opcional (P2)**: carimbo RFC 3161 do `chainHead` numa TSA pública quando houver rede, e manifesto C2PA.

---

## 11. Proteção da prova

### 11.1 Em repouso
- Cada segmento é criptografado com **AES-256-GCM em blocos** (um registro por fragmento, nonce derivado do índice, AAD = índice + flag de último) com uma chave de sessão.
- A chave de sessão é embrulhada por uma chave do Keystore.
- **Modo avançado opcional**: a chave de sessão é embrulhada pela **chave pública** do usuário (X25519). O aparelho grava, mas **não consegue** abrir a prova sem a chave privada, que o usuário guarda fora do celular. Explique bem a troca: mais segurança, sem poder assistir no próprio celular.
- Criptografe por fragmento já na escrita (não só ao fechar o segmento), para que um arquivo interrompido continue decifrável até o último fragmento completo.

### 11.2 Contra apagar
- Excluir uma sessão exige autenticação forte (`BiometricPrompt` com `DEVICE_CREDENTIAL`) e espera de 10 s com confirmação.
- Sessões ainda não enviadas mostram o aviso "ainda não está em lugar seguro".
- Sem "apagar tudo". Sem exclusão automática.

### 11.3 Para fora do aparelho (P1, mas a arquitetura nasce pronta)
- **Destinos plugáveis** (interface `UploadDestination`):
  1. **Telegram Bot API**: `sendDocument` para um chat privado do usuário. Fácil de configurar; limite de ~50 MB por arquivo, por isso segmentos de 30 s.
  2. **WebDAV / Nextcloud** (PUT).
  3. **S3 compatível** (assinatura SigV4: AWS, Cloudflare R2, Backblaze B2, MinIO).
  4. Google Drive (P2, OAuth via AppAuth).
- O envio é feito **pelo próprio `CaptureService` durante a sessão** (já está em primeiro plano) e pelo WorkManager depois (expedited, com retry exponencial e rede móvel permitida por padrão).
- **Prioridade**: manifesto parcial + localização → áudio → fotos-chave → vídeo traseiro → frontal → tela.
- Só sobe conteúdo **criptografado**. As credenciais ficam no armazenamento criptografado do Keystore.
- O painel mostra "Na nuvem: X de Y segmentos".

### 11.4 Alerta para contatos (P1, desligado por padrão)
- SMS (`SmsManager`, permissão `SEND_SMS`, escolha do chip em dual SIM) para até 3 contatos: "Caixa Preta acionada às 21:14. Local: https://maps.google.com/?q=lat,lon (±12 m). Bateria 46%."
- Atualização opcional a cada 5 min.
- Registre envio e falha no log da sessão. Nunca diga na interface que a mensagem chegou; só que foi enviada ao sistema.

### 11.5 Recomendações de sistema na Prontidão
- Bloqueio de tela forte.
- Proteções antirroubo do Android, quando o aparelho oferecer (Bloqueio por detecção de roubo, Bloqueio offline).
- Na Samsung, verificar se existe a opção que **exige desbloqueio para desligar ou mudar rede** (o nome varia por versão: confira no aparelho e documente).
- Explique o porquê de cada item: impedir que desliguem o celular antes do upload.

---

## 12. Discrição sem mentir

### 12.1 Disparo invisível
Tela preta por ≤ 2,5 s e volta ao estado anterior. Sem som, sem toast.

### 12.2 Vibração como linguagem (configurável)
| Evento | Padrão |
|---|---|
| Gravação iniciada | 1 vibração longa (300 ms) |
| Marcador registrado | 2 curtas |
| Gravação parada | 3 curtas |
| Falha / degradado | longa-curta-longa |
| Modo silencioso total | nenhuma |

### 12.3 Notificação
- Canal de importância baixa, sem som, sem badge, ícone neutro e `VISIBILITY_SECRET` (oculta na tela de bloqueio segura).
- Texto padrão "Sincronizando" / "Em andamento", editável pelo usuário.
- Ação "Parar" com `setAuthenticationRequired(true)` (API 31+), para que só quem desbloqueia possa parar.

### 12.4 Disfarce (opcional)
`activity-alias` alternativas com nome e ícone neutros ("Notas", "Calculadora"). Trocar o disfarce não mexe nas sessões.

### 12.5 O que não dá para esconder (mostrar ao usuário com clareza)
- A bolinha verde de câmera/microfone.
- O ícone na barra de status (alguns sistemas).
- O diálogo de gravação de tela.
- Aparecer em "Apps ativos" no painel rápido.

---

## 13. Design (estratégia visual completa)

### 13.1 Conceito
**"Instrumento de cabine."** O app parece um equipamento de voo: preto profundo, números grandes e monoespaçados, rótulos curtos em caixa alta, uma única cor de marca, o **laranja internacional** da caixa-preta real, e o **vermelho de gravação** reservado exclusivamente para "está gravando".

- Lido em 1 segundo, sob estresse, no sol, com a mão tremendo.
- Zero decoração.
- Cada cor significa uma coisa só.

### 13.2 Tokens
| Token | Hex | Uso |
|---|---|---|
| `bg` | `#0A0A0B` | fundo de todas as telas |
| `surface` | `#141416` | cartões |
| `surface2` | `#1D1D20` | linhas, campos |
| `line` | `#2A2A2E` | divisores, contornos |
| `text` | `#F2F2F4` | texto principal |
| `text2` | `#9A9AA2` | texto secundário |
| `brand` | `#FF4F00` | laranja internacional: marca, foco, ação principal em repouso |
| `rec` | `#FF2B2B` | **somente** estado "gravando" |
| `ok` | `#2FD158` | pronto / verificado |
| `warn` | `#FFB020` | atenção / degradado |

- Contraste do texto principal ≥ 15:1.
- Nada de gradiente.
- Tema único escuro (decisão de produto: discrição e legibilidade noturna). Respeite "alto contraste" e o tamanho de fonte do sistema.

### 13.3 Tipografia (fontes abertas, OFL, embutidas)
- **Barlow Condensed** SemiBold/Bold: títulos e rótulos em caixa alta (letter-spacing +6%).
- **IBM Plex Sans**: texto corrido.
- **JetBrains Mono**: cronômetro, horários, hashes, tamanhos (`tnum`).
- Escala: 12 / 14 / 16 / 20 / 28 / 44 / 64 sp. O cronômetro usa 64 sp.

### 13.4 Ícone
- Adaptativo: fundo `#0A0A0B`, um quadrado de cantos levemente arredondados em `#FF4F00` (a "caixa") com um ponto branco no canto superior direito (o "REC").
- Camada **monocromática** para ícones temáticos (Android 13+).
- Ícone de notificação: só a silhueta, sem texto.

### 13.5 Telas

**1. Painel** (tela inicial)
- Topo: "CAIXA PRETA" pequeno em `text2` + selo de estado à direita: `PRONTO` (verde), `ATENÇÃO` (âmbar, com contagem de pendências da Prontidão) ou `GRAVANDO` (vermelho).
- Centro: **botão circular de 220 dp**.
  - Em repouso: anel `brand` e "GRAVAR TUDO". Um toque inicia, sem confirmação.
  - Gravando: disco `rec` pulsando suavemente (1 Hz; sem pulso se "reduzir movimento"), cronômetro `00:12:34` em JetBrains Mono e a legenda "Segure para parar".
  - Parar = segurar 2 s + autenticação.
- Abaixo: **grade de fontes** com 6 itens: Traseira · Frontal · Microfone · Tela · Local · Nuvem. Cada item tem ícone, estado (●/○/⚠/✕) e um detalhe curto ("1080p", "não suportada", "bloqueado", "3/5 enviados").
- Rodapé: "Última sessão: ontem 21:14 · 12 min · verificada ✓".

**2. Prontidão**
- Pontuação "Pronto para agir: 9/11" e uma lista de itens, cada um com ✓ ou "Corrigir" (atalho direto para a tela certa do sistema):
  - câmera, microfone, localização, notificações;
  - "Sobrepor a outros apps";
  - bateria sem restrição;
  - gatilho configurado (com instruções por fabricante e um botão "Testar gatilho" que detecta se o último disparo veio do botão físico);
  - câmeras simultâneas (informativo);
  - espaço livre (≥ 2 h no degrau A);
  - destino de upload testado;
  - bloqueio de tela forte;
  - autoteste dos últimos 7 dias.
- Botão **"Testar agora"** (F15): grava 5 s, valida todos os arquivos (`MediaExtractor`: duração > 0, trilhas presentes, PTS crescente) e mostra o resultado por fonte.

**3. Provas** (cofre, exige autenticação)
- Lista de **sessões** (não de arquivos): data/hora, duração, fontes (ícones), local (bairro/cidade, se houver geocodificação **offline**; senão coordenadas), estado (✓ verificada / ☁ na nuvem / ⚠ interrompida).
- Detalhe da sessão:
  - player **sincronizado** em grade (traseira grande, frontal e tela em miniaturas, toque para trocar), linha do tempo com marcadores e lacunas;
  - mapa estático do trajeto, renderizado localmente, sem SDK de mapas;
  - aba "Integridade": hashes, cadeia, assinatura, botão "Verificar agora";
  - ações: Exportar pacote de prova, Compartilhar (abre o compartilhamento do sistema), Enviar para a nuvem agora.

**4. Gatilhos**: um cartão por método da seção 7, com estado, passo a passo ilustrado por fabricante e teste.

**5. Ajustes**:
- Qualidade (degrau inicial), duração dos segmentos, "Incluir tela";
- câmera prioritária;
- vibração, notificação discreta, disfarce;
- destinos de upload, contatos de emergência;
- PIN do app;
- idioma;
- "Sobre a prova", com a base legal e como verificar.

**6. Primeira execução** (4 passos, menos de 2 minutos):
1. "Para que serve" (uma frase + a regra ética).
2. Permissões, pedidas em contexto.
3. Configurar o gatilho principal do seu aparelho (detecção automática de Samsung/Pixel).
4. Autoteste.

### 13.6 Textos (tom de voz)
Direto, calmo, sem jargão e sem alarmismo. Exemplos:
- "Pronto. Aperte duas vezes o botão lateral para gravar tudo."
- "Câmera frontal: este aparelho não grava as duas câmeras ao mesmo tempo. Gravando a traseira."
- "Tela: o Android exige a sua confirmação e ela não aparece com o celular bloqueado. Câmeras e áudio continuam."
- "3 de 7 partes ainda não estão na nuvem. Elas sobem assim que houver internet."

### 13.7 Acessibilidade
- Alvos de toque ≥ 48 dp (o botão principal tem 220 dp).
- Rótulos para TalkBack em tudo, com o estado lido em voz ("Gravando há 12 minutos").
- Nunca dependa só de cor: todo estado também tem ícone e palavra.
- Suporte a fonte grande sem cortar o cronômetro.

---

## 14. Permissões e manifesto

| Permissão | Por quê |
|---|---|
| `CAMERA`, `RECORD_AUDIO` | gravação |
| `ACCESS_FINE_LOCATION` (+ `COARSE`) | trilha de localização da prova (em primeiro plano, via tipo de serviço `location`) |
| `FOREGROUND_SERVICE` + `_CAMERA`, `_MICROPHONE`, `_LOCATION`, `_MEDIA_PROJECTION`, `_SPECIAL_USE`, `_DATA_SYNC` | tipos de serviço no Android 14+ |
| `POST_NOTIFICATIONS` | notificação do serviço (13+) |
| `SYSTEM_ALERT_WINDOW` | isenção para abrir a TriggerActivity a partir do detector de botão |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | não ser morto pelo sistema |
| `WAKE_LOCK`, `VIBRATE`, `RECEIVE_BOOT_COMPLETED` | operação contínua, vibração, religar o detector |
| `INTERNET`, `ACCESS_NETWORK_STATE` | upload (P1) |
| `SEND_SMS` (P1, opcional), `USE_BIOMETRIC` | alerta e autenticação |

- **Não** peça permissões de armazenamento: tudo fica no armazenamento interno e a exportação usa SAF ou compartilhamento.
- `allowBackup=false`.
- `networkSecurityConfig` só com HTTPS.

---

## 15. Casos de borda (comportamento esperado)

| Situação | Comportamento |
|---|---|
| Disparo durante uma gravação | marcador + 2 vibrações curtas, nunca para |
| Dois disparos em < 1,5 s | debounce (um só) |
| Celular bloqueado | câmeras + áudio + local; tela pulada e registrada |
| Tela apagada e bolso | idem; sem acender a tela à toa |
| Ligação recebida ou feita | vídeo continua; trecho de áudio marcado como silenciado se o sistema silenciar |
| Outro app abre a câmera | aquele fluxo entra em lacuna e tenta voltar; o resto segue |
| Fone Bluetooth conecta/desconecta | o áudio segue no mic do aparelho (nunca trocar sozinho para SCO no meio da sessão) |
| Superaquecimento | desce degraus; nunca para sozinho |
| Pouco espaço / pouca bateria | seção 9.6 |
| App morto à força / crash | o processo `:capture` reinicia se possível (`START_STICKY`); na próxima abertura recupera, sela e marca "interrompida" |
| Reboot no meio | idem; não reinicia a gravação no boot (Android 15) |
| Permissão revogada no meio | fluxo afetado para e o evento é registrado |
| Sem internet / modo avião | grava normal; fila de upload espera |
| Relógio do sistema alterado | o elapsed garante a ordem; registrar o salto |
| Celular dobrável / rotação | sem interrupção; orientação nos metadados |
| Perfil de trabalho / Espaço privado | documentar; o app roda no perfil em que foi instalado |

---

## 16. Testes e qualidade

### 16.1 Automatizados
- **Unitários** (JVM): cadeia de hashes, manifesto canônico, criptografia em blocos (ida e volta, senha errada, truncamento, adulteração), escada de degraus, debounce, máquina de estados, parser de configurações.
- **Instrumentados** (emulador com câmera virtual): o serviço inicia, grava 10 s, sela, e o verificador aprova.
- **Verificador Python** com fixtures adulteradas.

### 16.2 Roteiro por ADB (rodar num aparelho real e anexar a saída ao relatório)
```bash
PKG=io.github.kauansilvestre.caixapreta
adb install -r app-release.apk
adb shell pm grant $PKG android.permission.CAMERA
adb shell pm grant $PKG android.permission.RECORD_AUDIO
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell dumpsys deviceidle whitelist +$PKG
# disparo direto
adb shell am start -n $PKG/.triggers.TriggerActivity --es source adb
# duplo liga/desliga (detector 7.4) — o gesto físico da Samsung precisa ser testado à mão
adb shell 'input keyevent KEYCODE_POWER; sleep 0.25; input keyevent KEYCODE_POWER'
# falhas
adb shell cmd thermalservice override-status 4 ; adb shell cmd thermalservice reset
adb shell dumpsys battery set level 3 ; adb shell dumpsys battery reset
adb shell cmd connectivity airplane-mode enable ; adb shell cmd connectivity airplane-mode disable
adb shell am start -a android.media.action.VIDEO_CAPTURE   # outro app pega a câmera
adb shell am crash $PKG                                    # crash forçado
adb shell dumpsys deviceidle force-idle                    # Doze
# coleta e verificação
adb exec-out run-as $PKG tar c files/sessions > sessions.tar   # build debug
ffprobe -v error -show_streams -show_format back/000.mp4
ffmpeg -v error -i back/000.mp4 -f null -
python3 tools/verify/caixapreta_verify.py sessions/<id>/
adb logcat -s CaixaPreta:V
```

### 16.3 Matriz de aparelhos (mínimo)
- Samsung intermediário (Galaxy A5x, One UI 7/8).
- Samsung topo de linha (Galaxy S2x).
- Pixel (Android 16).
- Xiaomi/Redmi (HyperOS).
- Um Android 10 antigo.

Em cada um: bloqueado/desbloqueado × tela ligada/apagada × cada gatilho.

### 16.4 Critérios de aceitação (meça e reporte valores reais, não os alvos)
- Disparo → áudio ≤ 700 ms; disparo → vídeo traseiro ≤ 1,5 s desbloqueado / ≤ 2 s bloqueado.
- Tela preta visível ≤ 2,5 s e retorno ao estado anterior.
- Crash forçado: perda ≤ 1 s por fluxo, e todos os arquivos tocam no VLC e passam no `ffmpeg -f null`.
- 2 h contínuas (câmeras + áudio) num intermediário sem travar; a escada térmica atua em vez de parar.
- Adulterar 1 byte de qualquer segmento faz o verificador falhar.
- Com upload configurado em 4G: o áudio de cada segmento chega ao destino em ≤ 15 s após o fechamento.

---

## 17. Build, CI e entrega

- **GitHub Actions** (`.github/workflows/build.yml`):
  - a cada push: `assembleDebug`, testes unitários, lint e upload do APK como artefato;
  - em tag `v*` ou manualmente: `assembleRelease` **assinado** e publicação de uma Release com os APKs.
- Assinatura pelos secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`. **Sem os secrets, gere o APK sem assinatura em vez de falhar** e avise no log.
- Documente no README como gerar a chave uma única vez:
  ```bash
  keytool -genkeypair -v -keystore caixapreta.jks -alias caixapreta -keyalg RSA -keysize 4096 -validity 36500
  ```
- R8 ligado no release com regras para Room e serialização. Teste o APK release, não só o debug.
- APK universal + `arm64-v8a`. Nome do arquivo: `CaixaPreta-<versão>-<abi>.apk`.
- **Licença**: se reutilizar qualquer código do FadCam/KCam, o projeto **é GPL-3.0** e deve manter os créditos. Recomendação: publique como GPL-3.0 de qualquer forma. Código aberto aumenta a credibilidade da prova, porque qualquer perito pode auditar como ela foi gerada.
- README em pt-BR:
  - o que é;
  - como instalar o APK;
  - como configurar o gatilho por fabricante;
  - limites do Android;
  - como verificar uma prova;
  - base legal resumida;
  - créditos (FadCam e projetos de inspiração).

---

## 18. Plano de execução em fases

| Fase | Entregas | Critério de saída |
|---|---|---|
| **0. Prova de viabilidade** (1º) | Projeto vazio + CI verde. Spike 1: `TriggerActivity` sobre o bloqueio inicia um serviço que grava 10 s de áudio + traseira. Spike 2: câmeras concorrentes no aparelho-alvo. Spike 3: tecla lateral Samsung abrindo a alias com o aparelho bloqueado. | Relatório `docs/FASE0.md` com resultados reais em aparelho. Se não houver aparelho, marcar "não verificado" e pedir ao usuário. |
| **1. MVP do disparo** | Gatilhos 7.1–7.4. Áudio único + traseira + fMP4 segmentado + sessão + Painel mínimo + Prontidão básica. | Disparo funciona bloqueado e desbloqueado; crash não corrompe; APK no CI. |
| **2. Tudo ao mesmo tempo** | Frontal (concorrente ou PiP), tela após consentimento, localização, marcadores, escada de qualidade, políticas 9.6. | Testes 16.2 passando no aparelho-alvo. |
| **3. Prova que vale** | Hashes, cadeia, Keystore + atestação, manifesto, criptografia em repouso, cofre com autenticação, exportação, verificador Python. | Adulteração detectada; exportação abre e verifica no PC. |
| **4. Prova que sobrevive** | Upload (Telegram, WebDAV, S3), fotos-chave, alerta SMS opcional, notificação com ação autenticada. | Áudio na nuvem ≤ 15 s; celular "tomado" (modo avião logo após 60 s) ainda deixa a prova parcial no destino. |
| **5. Acabamento** | Design completo da seção 13, ícone, onboarding, disfarce, i18n pt-BR/en, acessibilidade, README, Release assinada. | Checklist de aceitação 16.4 com números medidos; APK assinado publicado. |

---

## 19. Definição de pronto e formato do relatório

**Pronto** = o build está verde no CI, os testes passam, o APK release está publicado e os critérios 16.4 foram **medidos** num aparelho real (ou marcados explicitamente como "não verificado"), sem regressões nas fases anteriores.

Ao terminar cada fase, entregue um relatório curto em pt-BR:
1. O que foi feito (com links de commits).
2. **Como foi verificado**: logs, números medidos, prints.
3. O que **não** funciona ou não foi verificado, e por quê.
4. Próximo passo.
5. Link direto para o APK (artefato do CI ou Release).

Comece agora pela **Fase 0**. Antes de escrever código, crie `docs/PLANO.md` e `docs/DECISOES.md` e confirme com o usuário apenas:
- o modelo do aparelho principal;
- se ele quer upload já na primeira versão e para qual destino.
