// PlayBox Offline — configuração do Gradle
// Estrutura multi-módulo alinhada ao plano (Fase 2 do cronograma):
//   :app               -> shell do aplicativo (navegação, catálogo, telas)
//   :core:database     -> Room (estados de instalação, persistência local)
//   :core:catalog      -> catálogo embutido/remoto + repositório unificado
//   :core:designsystem -> tema claro/escuro e componentes visuais compartilhados
// Módulos futuros (Fases 3/4, criados quando ganharem conteúdo real):
//   :core:downloads, :core:packs, :core:gameapi, :games:damas, :games:xadrez, etc.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PlayBoxOffline"

include(":app")
include(":core:database")
include(":core:catalog")
include(":core:designsystem")
include(":core:gameapi")
include(":core:downloads")
include(":core:packs")
include(":games:damas")
include(":games:xadrez")
include(":games:domino")
include(":games:imperio")
include(":games:sushi")
