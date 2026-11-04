# Sentinela

Registrador pessoal de emergência para Android. **Local-first**, orientado à
**preservação de evidências** e **transparente por design**: cada captura é
sempre visível ao usuário por uma notificação persistente e pelos indicadores de
câmera/microfone do Android. O app **não** oculta, disfarça nem mascara a
gravação — isso é intencional e está alinhado às políticas do Google Play.

> Grave apenas o que você tem direito de gravar. As leis sobre gravação de
> áudio, vídeo e tela variam por jurisdição.

## O que o MVP faz

Configura uma vez → aciona fisicamente → o Sentinela confirma que a captura
realmente começou → continua gravando em segundo plano dentro das regras do
Android → novo acionamento encerra → arquivos são finalizados, verificados
(SHA-256), indexados e preservados.

Quatro perfis:

| Perfil | Fontes |
|---|---|
| **Áudio** | microfone |
| **Câmera + áudio** | microfone + câmera (traseira/frontal) |
| **Tela + áudio** | microfone + tela (com consentimento) |
| **Completo compatível** | ativa apenas as fontes que o aparelho e a sessão autorizarem |

### Princípio central: uma sessão não é binária

Cada fonte é uma unidade independente com estado próprio. Se o áudio interno
falhar mas microfone e câmera iniciarem, a sessão continua. O manifesto registra
exatamente **o que foi pedido, o que iniciou e o que falhou**:

```
status = PARTIAL_SUCCESS
microphone = ACTIVE
rear_camera = ACTIVE
front_camera = UNSUPPORTED
screen = USER_DENIED
```

## Limites reais do Android respeitados pelo projeto

- **Captura de tela** (`MediaProjection`) exige consentimento do usuário a cada
  sessão; em Android 14+ o token é de uso único e o sistema pode encerrar a
  projeção (ex.: ao bloquear a tela). Não é prometida como captura automática.
- **Gravação de chamada telefônica** (dois lados) depende de
  `CAPTURE_AUDIO_OUTPUT`, reservada a componentes do sistema — **fora do MVP**.
- **Câmera frontal + traseira simultâneas** só em aparelhos com
  `FEATURE_CAMERA_CONCURRENT` — detectado em runtime, nunca forçado.
- **Indicadores de câmera/microfone** (Android 12+) nunca são ocultados.
- Foreground services declaram os tipos `camera`, `microphone` e
  `mediaProjection`, enviando a `startForeground()` apenas os efetivamente usados.

## Arquitetura

```
TRIGGER (Side Key / QS Tile / botão no app)
        │
        ▼
TriggerActivity (brevemente visível → satisfaz "while-in-use" do Android 14+)
        │
        ▼
RecordingService (foreground, LifecycleService)
        │
        ▼
SessionController ── inicia cada fonte independentemente
        │
   ┌────┼─────────────┬──────────────┐
   ▼    ▼             ▼              ▼
AudioCaptureEngine  CameraCaptureEngine  ScreenCaptureEngine
(MediaRecorder/AAC) (CameraX VideoCapture) (MediaProjection)
        │
        ▼
SessionLedger ── journal encadeado por hash + manifesto atômico + SHA-256 por segmento
        │
        ▼
SessionStore (MP4/AAC/JSON em disco)  ←  RecoveryEngine (reconcilia quedas)
```

Pacotes principais:

- `core/` — modelos, armazenamento, hashing, permissões (lógica pura, testável).
- `capture/` — engines de áudio, câmera e tela, cada um atrás de `CaptureEngine`.
- `session/` — `SessionController`, `SessionLedger`, serviço de primeiro plano, recuperação.
- `trigger/` — Activity de disparo e Quick Settings Tile.
- `data/` — índice Room (cache reconstruível) e `ProfileStore`.
- `ui/` — Compose: Início, Perfis, Registros.

### Garantias de integridade

- **Journal** (`journal.jsonl`): eventos somente-anexação encadeados por
  SHA-256, com `fsync` a cada entrada. Adulterar qualquer linha quebra a cadeia,
  detectável por `EventJournal.verify`.
- **Manifesto** (`manifest.json`): escrito de forma atômica (temp + `fsync` +
  rename). É a fonte da verdade; o índice Room é só um cache.
- **Segmentos**: áudio em AAC/ADTS (cada quadro autossuficiente — sobrevive a
  truncamento); cada segmento recebe SHA-256 ao fechar. MP4 sem a caixa `moov` é
  marcado `POSSIBLY_CORRUPT`.
- **Recuperação**: na inicialização, sessões deixadas abertas por uma queda são
  reindexadas (bytes + hash) e marcadas `RECOVERED`, sem apagar nada.

## Build

Requer Android SDK (compileSdk 35) e JDK 17. Com o `local.properties` apontando
para o SDK (`sdk.dir=...`):

```bash
./gradlew :app:assembleDebug        # gera o APK debug
./gradlew :app:testDebugUnitTest    # 31 testes de lógica pura
./gradlew :app:lintDebug            # lint (sem erros)
```

minSdk 26 · targetSdk 35 · Kotlin 2.0 · Jetpack Compose · CameraX · Room.

## Status

MVP em desenvolvimento. Compila, passa nos testes unitários e no lint. As
capturas que dependem de hardware/versão (tela bloqueada, câmera concorrente)
precisam de validação em aparelhos reais, por modelo e versão de One UI/Android.
