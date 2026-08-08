package dev.deckbuild.core

import dev.deckbuild.core.content.CardLibrary
import dev.deckbuild.core.engine.CardInstance
import dev.deckbuild.core.engine.EssenceSolver
import dev.deckbuild.core.engine.GameState
import dev.deckbuild.core.engine.Permanent
import dev.deckbuild.core.engine.PlayerState
import dev.deckbuild.core.engine.Ruleset
import dev.deckbuild.core.model.Aspect
import dev.deckbuild.core.model.Cost
import dev.deckbuild.core.model.Side
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EssenceSolverTest {

    private fun stateWithSources(vararg aspects: Aspect): GameState {
        val state = GameState(
            Ruleset(),
            PlayerState(Side.SPIELER, 30),
            PlayerState(Side.GEGNER, 30),
        )
        for (aspect in aspects) {
            val def = CardLibrary.QUELLEN.getValue(aspect)
            val permanent = Permanent(CardInstance(state.allocateInstanceId(), def), Side.SPIELER)
            permanent.summoningSick = false
            state.battlefield += permanent
        }
        return state
    }

    @Test
    fun `farblose Kosten werden von beliebigen Quellen bezahlt`() {
        val state = stateWithSources(Aspect.GLUT, Aspect.FLUT, Aspect.HAIN)
        val payment = EssenceSolver.solve(state, Side.SPIELER, Cost(generic = 3))
        assertNotNull(payment)
        assertEquals(3, payment!!.tapSourceIds.size)
    }

    @Test
    fun `farbige Kosten brauchen passende Quellen`() {
        val state = stateWithSources(Aspect.GLUT, Aspect.GLUT, Aspect.FLUT)
        assertTrue(state.canPay(Side.SPIELER, Cost.colored(Aspect.GLUT, 2)))
        assertFalse(state.canPay(Side.SPIELER, Cost.colored(Aspect.GLUT, 3)))
    }

    @Test
    fun `farbige und farblose Anteile konkurrieren korrekt`() {
        // Zwei Glut- und eine Flutquelle koennen GG1 bezahlen, aber nicht GG2.
        val state = stateWithSources(Aspect.GLUT, Aspect.GLUT, Aspect.FLUT)
        assertTrue(state.canPay(Side.SPIELER, Cost.colored(Aspect.GLUT, 2, generic = 1)))
        assertFalse(state.canPay(Side.SPIELER, Cost.colored(Aspect.GLUT, 2, generic = 2)))
    }

    @Test
    fun `verdeckte Quellen zahlen nur farblose Kosten`() {
        val state = stateWithSources(Aspect.GLUT)
        val plain = CardLibrary.find("glut_aschekrieger")!!
        val instance = CardInstance(state.allocateInstanceId(), plain)
        instance.faceDownSource = true
        val permanent = Permanent(instance, Side.SPIELER)
        permanent.summoningSick = false
        state.battlefield += permanent

        assertTrue(state.canPay(Side.SPIELER, Cost.colored(Aspect.GLUT, 1, generic = 1)))
        assertFalse(state.canPay(Side.SPIELER, Cost.colored(Aspect.GLUT, 2)))
    }

    @Test
    fun `getappte Quellen zaehlen nicht mit`() {
        val state = stateWithSources(Aspect.HAIN, Aspect.HAIN)
        state.battlefield.first().tapped = true
        assertTrue(state.canPay(Side.SPIELER, Cost.colored(Aspect.HAIN, 1)))
        assertFalse(state.canPay(Side.SPIELER, Cost.colored(Aspect.HAIN, 2)))
    }

    @Test
    fun `Zahlung tappt genau die benoetigten Quellen`() {
        val state = stateWithSources(Aspect.GLUT, Aspect.FLUT, Aspect.HAIN, Aspect.LICHT)
        val cost = Cost.colored(Aspect.FLUT, 1, generic = 2)
        val payment = EssenceSolver.solve(state, Side.SPIELER, cost)!!
        EssenceSolver.apply(state, Side.SPIELER, payment)
        assertEquals(3, state.battlefield.count { it.tapped })
        assertEquals(1, state.battlefield.count { !it.tapped })
    }

    @Test
    fun `Essenz im Pool wird vor Quellen ausgegeben`() {
        val state = stateWithSources(Aspect.GLUT)
        state.player.addEssence(Aspect.NEUTRAL, 2)
        val payment = EssenceSolver.solve(state, Side.SPIELER, Cost(generic = 2))!!
        EssenceSolver.apply(state, Side.SPIELER, payment)
        assertEquals(0, state.player.poolTotal())
        assertTrue(state.battlefield.none { it.tapped })
    }

    @Test
    fun `unbezahlbare Kosten liefern kein Ergebnis`() {
        val state = stateWithSources(Aspect.GLUT)
        assertNull(EssenceSolver.solve(state, Side.SPIELER, Cost(generic = 5)))
    }
}
