# Sentinela — estado atual e continuidade

App Android de **segurança pessoal**: registrar rápido uma situação de risco e
preservar os arquivos. Edição pessoal, construída sobre a base KCam/FadCam
(GPL-3.0, `anonfaded/FadCam@e59f58e`). Namespace Java continua `com.fadcam`
(facilita puxar melhorias do upstream); o código próprio fica em
`com.fadcam.kcam.*`.

## Como compilar (única via neste ambiente)
Compilação **local é impossível aqui**: a política de rede bloqueia
`dl.google.com` (SDK Android / artefatos do Google Maven) — confirmado com 403
do proxy. A compilação acontece no **GitHub Actions** (`.github/workflows/kcam-build.yml`):
- push em `claude/**` → job `build`: `:app:assembleDefaultDebug` + testes
  unitários `com.fadcam.kcam.*`; artefato `KCam-debug-<sha>` (APK instalável).
- tag `v*` ou run manual com `release` → job `release` (assinado se houver os
  secrets `KCAM_*`, senão UNSIGNED).

## Último marco concluído
- **Marco A (estado real conhecido):** 13 commits anteriores verificados; base =
  FadCam@e59f58e; CI já compilava verde e gerou APKs. Upstream não tem commits
  após e59f58e (fork está na ponta do upstream).
- **Incremento 1 (commit `1b841cf`, branch `claude/practical-cray-pconk0`):**
  - Identidade **Sentinela** (`kcam.appName`, `applicationId
    io.github.kauansilvestre.sentinela`).
  - Feedback por vibração ligado ao **estado real** (não ao comando): áudio
    (FadMic) passou a vibrar início/parada reais; padrão distinto de **falha**
    (`Utils.vibrateRecordingError`) na câmera, áudio e gravação rápida; removido
    o buzz "ao receber comando" e o toggle de vibração duplicado (agora um único
    controle: a configuração Haptic do app).
  - `com.fadcam.kcam.integrity.SessionIntegrity`: SHA-256 + manifesto JSON de
    sessão (data/hora UTC, aparelho, versão) + testes unitários. **Ainda não
    ligado à finalização** (bloco verificável; integração pendente).

## Próxima ação concreta
1. Confirmar CI do commit `1b841cf` verde (build + testes) e pegar o APK
   instalável `KCam-debug-<sha>`.
2. **Ligar o SessionIntegrity à finalização real** da gravação (câmera modo
   File e SAF; depois áudio/tela), gravando o sidecar fora da thread principal,
   sem tocar no arquivo original.
3. **Home minimalista "Sentinela"** em pt-BR: fluxo evidente (configurar →
   acionar → encerrar → acessar), escondendo os extras do FadCam.
4. CI: assinatura de desenvolvimento estável, nome de APK com versão/commit,
   SHA-256 publicado; remover variantes disfarçadas do build focado.

## Prioridades (do pedido do usuário)
Integridade/preservação dos arquivos > funcionamento > segurança >
compatibilidade > novos recursos > acabamento > monetização.
Poucas coisas funcionando muito bem > muitas opções incompletas.
Discrição = mínima interferência visual, **respeitando** indicadores e
notificações obrigatórios do Android (nada de burlar o sistema).

## Acionamento por duplo clique no botão liga/desliga — realidade da plataforma
Apps comuns **não leem o botão liga/desliga** diretamente (proteção do Android).
O que existe e funciona (`com.fadcam.kcam.quick`):
- Samsung: "pressionar duas vezes a tecla lateral → abrir app" → escolher o
  atalho do app.
- Pixel/outros: gesto "duplo toque no liga/desliga abre a câmera" → escolher o
  app como câmera.
- Qualquer aparelho: atalho na tela inicial; e um watcher opcional que detecta a
  tela apagar/acender duas vezes rápido (precisa de "Sobrepor a outros apps").
A captura de tela **sempre** mostra a confirmação do sistema (Android 14+).
Nenhum desses burla o sistema; são os caminhos oficiais mais próximos do
"duplo clique".

## Testado x não testado
- **Testado automaticamente (CI):** compilação da variante debug; testes
  unitários de integridade (SHA-256, detecção de alteração, JSON) e dos módulos
  kcam existentes (crypt, schedule, profiles, thermal).
- **NÃO testado em aparelho real:** gravação, duplo clique, vibração honesta de
  início/parada/falha, captura de tela, recuperação. Depende de um celular
  Android conectado (não disponível neste ambiente).
