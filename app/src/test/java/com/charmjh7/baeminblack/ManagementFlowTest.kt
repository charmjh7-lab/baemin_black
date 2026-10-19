package com.charmjh7.baeminblack

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the shipped Compose screens with accessibility disabled, including Activity recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = BlackApplication::class)
class ManagementFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun button(label: String) = compose.onNode(
        hasClickAction() and hasAnyDescendant(hasText(label)), useUnmergedTree = true
    )
    @Test fun manuallyRegisterClassifyAndSaveMemoWithoutService() {
        compose.onNodeWithText("접근성 서비스 꺼짐").assertExists()
        button("가게 등록").performClick()
        compose.onNodeWithText("가게명 *").performTextInput("테스트 치킨")
        compose.onNodeWithText("지점명").performTextInput("강남점")
        button("저장").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("내 주문 경험 0건").fetchSemanticsNodes().isNotEmpty() }
        button("블랙리스트").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasAnyDescendant(hasText("블랙리스트")) and isSelected(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        button("메모 추가").performClick()
        compose.onNodeWithText("자유 메모").performTextInput("첫 주문 경험을 기억해요.")
        button("저장").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("첫 주문 경험을 기억해요.").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("첫 주문 경험을 기억해요.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("뒤로").performClick()
        compose.onNodeWithText("테스트 치킨").assertExists()
        compose.onAllNodesWithText("블랙리스트", useUnmergedTree = true).assertCountEquals(2)
    }
}
