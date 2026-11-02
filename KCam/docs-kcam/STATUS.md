# Sentinela — estado atual e continuidade

App Android de **segurança pessoal**: registrar rápido uma situação de risco e
preservar os arquivos. Edição pessoal sobre a base KCam/FadCam (GPL-3.0,
`anonfaded/FadCam@e59f58e`). Namespace Java continua `com.fadcam` (facilita
puxar melhorias do upstream); o código próprio fica em `com.fadcam.kcam.*`.

Branch de trabalho: `claude/practical-cray-pconk0`.

## Como compilar (única via neste ambiente)
Compilação local é impossível aqui (a rede bloqueia `dl.google.com` — SDK/Google
Maven, 403). Tudo passa pelo **GitHub Actions** (`.github/workflows/kcam-build.yml`,
na RAIZ do repo; o `KCam/.github/.../build-pro.yml` é do upstream e não roda):
- push em `claude/**` → job `build`: `:app:assembleDefaultDebug` + testes
  `com.fadcam.kcam.*` + **SHA-256 por APK** (no resumo da execução e em
  `SHA256SUMS.txt`); artefato `KCam-debug-<sha>` (APKs instaláveis).
- tag `v*` / run manual com `release` → job `release` (assina se houver os
  secrets `KCAM_*`, senão UNSIGNED), também com checksums.

## Feito e validado no CI (verde)
- **Identidade Sentinela**: `kcam.appName`, `applicationId
  io.github.kauansilvestre.sentinela`; textos "KCam"→"Sentinela" (rebrand
  idempotente) preservando atribuição ao FadCam e caminhos de pasta.
- **Vibração reflete o estado REAL** (não o comando): áudio passou a vibrar
  início/parada reais; **padrão distinto de falha** (`Utils.vibrateRecordingError`)
  na câmera, áudio e gravação rápida; um único controle (configuração Haptic).
- **Comprovante de integridade** (`com.fadcam.kcam.integrity.SessionIntegrity`):
  SHA-256 + manifesto de sessão (data/hora UTC, aparelho, versão). Gerado ao
  finalizar: sidecar ao lado (armazenamento interno/`file`) ou na pasta própria
  do app `getExternalFilesDir/integrity` (pasta personalizada/SAF). Nunca toca
  no original; falha é registrada e ignorada. Testes unitários do checksum/manifesto.
- **Variantes disfarçadas (Notes/Calc/Weather) removidas** (flavors, filtros,
  sourceSets, ícones). Mantido só o flavor `default`.
- **Home minimalista `SentinelaHomeActivity`** (iteração 1): botão grande
  GRAVAR/PARAR (delega à `QuickRecordActivity`), estado real, atalhos ao app
  completo; Splash abre a home; guarda de onboarding no 1º uso.

## NÃO validado (precisa de aparelho real)
Nada foi testado em celular (não há device aqui). Falta confirmar: aparência e
navegação da home; duplo clique no liga/desliga; vibração honesta; captura de
tela; gravação longa; recuperação; geração do comprovante em disco.

## Próxima ação concreta
1. Revisão visual da home no aparelho (pode exigir ajustes de layout/contraste).
2. Deep-link da home direto para a aba de Gravações e para as configurações de
   captura/perfis (hoje abrem o app completo na tela padrão).
3. Elapsed/tempo de gravação na home (via broadcast de estado do serviço).
4. Comprovante de integridade para os serviços de tela e dual-cam (hoje: câmera
   e áudio).
5. Assinatura de release estável depende de keystore do usuário (secrets
   `KCAM_*`): ver KCAM.md. O debug do CI é assinado com chave de debug efêmera,
   então atualizar sobre um debug pode exigir desinstalar.

## Prioridades (do pedido do usuário)
Integridade/preservação > funcionamento > segurança > compatibilidade > novos
recursos > acabamento > monetização. Poucas coisas muito bem feitas.
Discrição = mínima interferência visual, **respeitando** indicadores e
notificações obrigatórios do Android.
