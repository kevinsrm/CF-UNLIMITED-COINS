# CF LOCK

Aplicativo Android de bloqueio tipo quiosque, escrito em Kotlin. Este projeto foi reorganizado para abrir e compilar em IDEs Android que usam Gradle Kotlin DSL, incluindo o SmartIDE.

## Abrir no SmartIDE

Abra/importa a pasta raiz do repositório — a pasta que contém `settings.gradle.kts` — e aguarde a sincronização do Gradle. O projeto usa Android Gradle Plugin 8.10.1, Kotlin 2.1.20, JDK 17 e Android SDK 36.

Para compilar, escolha a variante `debug` no painel Build Variants e execute **Build / Assemble Project** (ou a tarefa `:app:assembleDebug`). O APK sai em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Para gerar a variante de release, execute `:app:assembleRelease`. O release é gerado sem minificação e sem assinatura configurada; assine o APK com a ferramenta de assinatura da IDE antes de distribuir/instalar quando necessário.

## Funcionalidades mantidas

- Tela de configuração para ativar administrador do dispositivo, escolher o app como tela inicial, permitir sobreposição e armar o bloqueio.
- Tela de bloqueio imersiva e superfície de sobreposição para reforçar o bloqueio.
- Serviço em primeiro plano, reativação após inicialização e watchdog periódico.
- Verificação do código, limitação de tentativas e estado persistente local.
- Desbloqueio que encerra o bloqueio, a sobreposição e o serviço.

O app não apaga, criptografa nem envia dados. A ativação de administrador e a permissão de sobreposição são concedidas pelo próprio Android; o comportamento também depende da versão do sistema e do fabricante. Teste primeiro em um aparelho/emulador que você possa recuperar. A recuperação por ADB documentada em [`app/README.md`](app/README.md) continua disponível.

## Estrutura

```text
settings.gradle.kts       configura o projeto Gradle e inclui :app
build.gradle.kts          versões dos plugins Android e Kotlin
app/build.gradle.kts      SDKs, identificador, variantes e compilação Kotlin
app/src/main/             Manifest, Kotlin e recursos Android convencionais
app/build.sh              build legado headless (opcional)
```

O build Gradle não depende do script headless. O `app/build.sh` antigo foi mantido como opção para ambientes com ferramentas Android já instaladas.
