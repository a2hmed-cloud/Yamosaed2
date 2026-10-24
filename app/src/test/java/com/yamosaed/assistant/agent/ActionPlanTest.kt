package com.yamosaed.assistant.agent

import org.junit.Assert.*
import org.junit.Test

class ActionPlanTest {
    @Test fun acceptsRegisteredLowRiskAction() {
        val result = ActionPlanParser.parse(
            """{"intent":"open_app","goal":"Open Spotify","steps":[{"tool":"AppLaunchTool","action":"open","arguments":{"appName":"Spotify"}}]}"""
        )
        assertTrue(result is PlanParseResult.Valid)
        val plan = (result as PlanParseResult.Valid).plan
        assertEquals("Spotify", plan.steps.single().arguments["appName"])
        assertFalse(plan.steps.single().requiresConfirmation)
    }

    @Test fun rejectsUnknownTool() {
        val result = ActionPlanParser.parse(
            """{"intent":"shell","goal":"run command","steps":[{"tool":"ShellTool","action":"execute","arguments":{"command":"id"}}]}"""
        )
        assertTrue(result is PlanParseResult.Invalid)
    }

    @Test fun modelCannotDisableConfirmationForSensitiveAction() {
        val result = ActionPlanParser.parse(
            """{"intent":"message","goal":"send message","steps":[{"tool":"MessagingTool","action":"compose","requiresConfirmation":false,"arguments":{"body":"Hi"}}]}"""
        )
        assertTrue(result is PlanParseResult.Valid)
        assertTrue((result as PlanParseResult.Valid).plan.steps.single().requiresConfirmation)
        assertEquals(ActionRisk.SENSITIVE, result.plan.steps.single().risk)
    }

    @Test fun clarificationPlanDoesNotNeedExecutionSteps() {
        val result = ActionPlanParser.parse(
            """{"intent":"clarify_contact","goal":"","needsClarification":true,"clarificationQuestion":"تقصد أنهي أحمد؟"}"""
        )
        assertTrue(result is PlanParseResult.Valid)
        assertTrue((result as PlanParseResult.Valid).plan.needsClarification)
        assertTrue((result as PlanParseResult.Valid).plan.steps.isEmpty())
    }

    @Test fun rejectsTooManySteps() {
        val steps = (1..13).joinToString(",") {
            """{"tool":"AppLaunchTool","action":"open","arguments":{"appName":"App"}}"""
        }
        assertTrue(ActionPlanParser.parse("""{"intent":"test","goal":"test","steps":[$steps]}""") is PlanParseResult.Invalid)
    }

    @Test fun localPlannerMarksMessagesAsSensitive() {
        val plan = LocalIntentPlanner.plan("ابعت لمحمد إني هتأخر")!!
        assertEquals(ActionRisk.SENSITIVE, plan.steps.single().risk)
        assertTrue(plan.steps.single().requiresConfirmation)
    }
}
