package com.swent.polyloop.screen

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.swent.polyloop.resources.C
import io.github.kakaocup.compose.node.element.ComposeScreen
import io.github.kakaocup.compose.node.element.KNode

class MainScreen(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<MainScreen>(
        semanticsProvider = semanticsProvider,
        viewBuilderAction = { hasTestTag(_root_ide_package_.com.swent.polyloop.resources.C.Tag.main_screen_container) },
    ) {

  val simpleText: KNode = child { hasTestTag(_root_ide_package_.com.swent.polyloop.resources.C.Tag.greeting) }
}
