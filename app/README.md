# CF LOCK — configuração e funcionamento

App de bloqueio tipo quiosque para Android 7.0 (API 24) até Android 16 (API 36). A migração para Gradle Kotlin DSL não altera os componentes nem o fluxo funcional do app. As fontes e recursos agora seguem a estrutura Android padrão em `src/main/`.

## Compilação no SmartIDE

Abra a pasta raiz do repositório, que contém `settings.gradle.kts`, e sincronize o Gradle. Requisitos: JDK 17 e Android SDK Platform 36. Para compilar, use `:app:assembleDebug` ou selecione a variante `debug` na IDE. O APK estará em `app/build/outputs/apk/debug/app-debug.apk`.

A variante release pode ser compilada com `:app:assembleRelease`. Ela está sem minificação e não tem keystore incluído; configure/execute a assinatura pelo SmartIDE ou Android SDK antes de distribuir. Não coloque keystore nem senhas no controle de versão.

## Componentes

- `Gate.kt`: verificação do código via SHA-256 e comparação sem saída antecipada.
- `Prefs.kt`: estado do bloqueio, tentativas, boots e cooldown em SharedPreferences.
- `Shield.kt`: flags imersivas, lock task, wake e verificação do launcher padrão.
- `LinkActivity.kt`: fluxo de configuração e ativação.
- `LockActivity.kt`: tela de bloqueio.
- `OverlayGuard.kt`: serviço legado desativado para não obstruir o teclado e a recuperação.
- `KeepAliveService.kt`: serviço de primeiro plano.
- `BootReceiver.kt` / `ScreenReceiver`: reativação após boot e eventos de tela.
- `GuardReceiver.kt`: receiver de administrador do dispositivo.

## Ativar e armar

1. Instale o APK e abra CF LOCK.
2. Siga os botões para conceder, se desejar, a permissão de administrador e escolha CF LOCK como tela inicial. A sobreposição não é mais utilizada.
3. Pressione **Armar bloqueio**. A linha de estado mostra se administrador, launcher e sobreposição estão ativos.
4. Para liberar, informe o código aceito na tela e pressione **Desbloquear**.

A permissão de administrador/launcher/sobreposição depende de confirmação explícita nas telas do Android. O app não é um device owner nem substitui políticas gerenciadas por uma organização; um dispositivo não-rooted mantém caminhos de recuperação do sistema.

## Recuperação

O app não apaga nem criptografa dados. Se necessário, use ADB:

- `adb shell pm clear com.cf.kiosk` limpa o estado local e a flag de bloqueio.
- `adb shell appops set com.cf.kiosk SYSTEM_ALERT_WINDOW deny` remove a permissão de sobreposição.
- `adb shell am force-stop com.cf.kiosk` interrompe o app; se lock task estiver ativo, reinicie em modo seguro ou remova o app administrador em Configurações > Segurança.
- Como último recurso, desative CF LOCK em Configurações > Apps ou remova o administrador em Configurações > Segurança > Apps de administrador do dispositivo.

O código aceito não é guardado em texto claro. A implementação e seu hash estão em `Gate.kt`; se for personalizar o código, regenere também os valores armazenados de acordo com a lógica da classe.

## Build headless legado (opcional)

`app/build.sh` continua disponível, mas não é usado pelo SmartIDE. Ele requer JDK, compilador Kotlin, Android SDK Build Tools e keystore locais; consulte o próprio script para variáveis de ambiente e caminhos necessários.
