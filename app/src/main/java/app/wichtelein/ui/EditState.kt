package app.wichtelein.ui

import app.wichtelein.anim.ElfAction
import app.wichtelein.anim.Pt
import app.wichtelein.anim.SceneryItem
import app.wichtelein.anim.ScriptStep

/** Alles, was man im Editor platzieren kann (ohne die Auswahl der Szene): Start, Weg, Aktionspunkte, Tür, Gegenstände. */
data class EditState(
    val start: Pt? = null,
    /** Startpunkt der Elfin, wenn beide auftreten */
    val start2: Pt? = null,
    val path: List<Pt> = emptyList(),
    val script: List<ScriptStep> = emptyList(),
    val door: Pt? = null,
    val doorScale: Float = 0.2f,
    val doorHingeLeft: Boolean = true,
    val doorRound: Float = 1f,
    /** Drehung der Tür um ihren Fußpunkt in Grad */
    val doorRot: Float = 0f,
    val scenery: List<SceneryItem> = emptyList(),
    val nextId: Int = 1,
    /** Es schneit von Anfang an (die Aktionen „Es schneit“ / „Schnee hört auf“ gehen zusätzlich) */
    val snow: Boolean = false,
)

/** Was gerade ausgewählt ist (zum Verschieben, Löschen, Größe ändern). */
sealed interface Sel {
    object Start : Sel
    object Start2 : Sel
    data class Path(val index: Int) : Sel
    data class Step(val index: Int) : Sel
    object Door : Sel
    data class Item(val id: Int) : Sel
}

/** Arten von Auswählbarem (damit ein Werkzeug nur „seine“ Dinge anfasst). */
enum class SelKind { START, PATH, STEP, DOOR, ITEM }

fun Sel.kind(): SelKind = when (this) {
    Sel.Start -> SelKind.START
    Sel.Start2 -> SelKind.START
    is Sel.Path -> SelKind.PATH
    is Sel.Step -> SelKind.STEP
    Sel.Door -> SelKind.DOOR
    is Sel.Item -> SelKind.ITEM
}

object EditOps {
    const val MAX_WAYPOINTS = 12
    const val MAX_STEPS = 24
    const val MAX_ITEMS = 24
    /** Punkte dürfen fast im ganzen Bild liegen (auch weit oben, z. B. auf einer Treppe) */
    const val MIN_Y = 0.03f

    fun clamp(p: Pt) = Pt(p.x.coerceIn(0.03f, 0.97f), p.y.coerceIn(MIN_Y, 0.96f))

    fun contains(s: EditState, sel: Sel): Boolean = when (sel) {
        Sel.Start -> s.start != null
        Sel.Start2 -> s.start2 != null
        is Sel.Path -> sel.index in s.path.indices
        is Sel.Step -> sel.index in s.script.indices && s.script[sel.index].at != null
        Sel.Door -> s.door != null
        is Sel.Item -> s.scenery.any { it.id == sel.id }
    }

    /** Verschiebt den ausgewählten Punkt an die neue Stelle [p]. */
    fun move(s: EditState, sel: Sel, p: Pt): EditState {
        val q = clamp(p)
        return when (sel) {
            Sel.Start -> s.copy(start = q)
            Sel.Start2 -> s.copy(start2 = q)
            is Sel.Path -> if (sel.index in s.path.indices) s.copy(path = s.path.toMutableList().also { it[sel.index] = q }) else s
            is Sel.Step -> if (contains(s, sel)) s.copy(script = s.script.toMutableList().also { it[sel.index] = it[sel.index].copy(at = q) }) else s
            Sel.Door -> if (s.door != null) s.copy(door = q) else s
            is Sel.Item -> s.copy(scenery = s.scenery.map { if (it.id == sel.id) it.copy(at = q) else it })
        }
    }

    /** Entfernt den ausgewählten Punkt oder Gegenstand. */
    fun remove(s: EditState, sel: Sel): EditState = when (sel) {
        Sel.Start -> s.copy(start = null)
        Sel.Start2 -> s.copy(start2 = null)
        is Sel.Path -> if (sel.index in s.path.indices) s.copy(path = s.path.filterIndexed { i, _ -> i != sel.index }) else s
        is Sel.Step -> if (sel.index in s.script.indices) s.copy(script = s.script.filterIndexed { i, _ -> i != sel.index }) else s
        Sel.Door -> s.copy(door = null)
        is Sel.Item -> s.copy(scenery = s.scenery.filter { it.id != sel.id })
    }

    /** Schiebt das Ausgewählte um ([dx], [dy]) relativ zur Bildgröße (Feineinstellung per Knopf). */
    fun nudge(s: EditState, sel: Sel, dx: Float, dy: Float): EditState {
        val p = position(s, sel) ?: return s
        return move(s, sel, Pt(p.x + dx, p.y + dy))
    }

    /** Aktuelle Stelle des Ausgewählten. */
    fun position(s: EditState, sel: Sel): Pt? = when (sel) {
        Sel.Start -> s.start
        Sel.Start2 -> s.start2
        is Sel.Path -> s.path.getOrNull(sel.index)
        is Sel.Step -> s.script.getOrNull(sel.index)?.at
        Sel.Door -> s.door
        is Sel.Item -> s.scenery.firstOrNull { it.id == sel.id }?.at
    }

    /** Eigene Sprechblase für die Aktion Nummer [index] (null = Standard). */
    fun setBubble(s: EditState, index: Int, spec: app.wichtelein.anim.BubbleSpec?): EditState =
        if (index !in s.script.indices) s else s.copy(script = s.script.toMutableList().also { it[index] = it[index].copy(bubble = spec) })

    fun addPath(s: EditState, p: Pt): EditState =
        if (s.path.size >= MAX_WAYPOINTS) s else s.copy(path = s.path + clamp(p))

    /** [who] = welche Figur die Aktion macht (0 = Wichtel, 1 = Elfin); zählt nur, wenn beide auftreten. */
    fun addStep(s: EditState, action: ElfAction, p: Pt?, who: Int = 0): EditState =
        if (s.script.size >= MAX_STEPS) s else s.copy(script = s.script + ScriptStep(action, p?.let { clamp(it) }, who = who))

    fun addItem(s: EditState, item: SceneryItem): EditState =
        if (s.scenery.size >= MAX_ITEMS) s else s.copy(scenery = s.scenery + item.copy(id = s.nextId, at = clamp(item.at)), nextId = s.nextId + 1)

    fun resizeItem(s: EditState, id: Int, scale: Float): EditState =
        s.copy(scenery = s.scenery.map { if (it.id == id) it.copy(scale = scale.coerceIn(0.05f, 0.9f)) else it })

    /** Winkel auf −180..180 Grad bringen. */
    fun wrapDeg(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    /**
     * Rechnet die Tippstelle ([x], [y]) in das Bild eines um [rot] Grad (im Uhrzeigersinn) gedrehten Gegenstands mit Fußpunkt [base]
     * zurück: Abstand zum Fußpunkt im ungedrehten Gegenstand, in Bildbreiten und -höhen. [aspect] = Breite / Höhe des Bildes.
     */
    fun toLocal(x: Float, y: Float, base: Pt, rot: Float, aspect: Float): Pair<Float, Float> {
        if (rot == 0f) return Pair(x - base.x, y - base.y)
        val th = Math.toRadians(rot.toDouble())
        val c = Math.cos(th).toFloat()
        val s = Math.sin(th).toFloat()
        val px = (x - base.x) * aspect
        val py = y - base.y
        return Pair((px * c + py * s) / aspect, -px * s + py * c)
    }

    /** Dreht einen Gegenstand um den Fußpunkt auf [deg] Grad (−180..180). */
    fun rotateItem(s: EditState, id: Int, deg: Float): EditState =
        s.copy(scenery = s.scenery.map { if (it.id == id) it.copy(rot = wrapDeg(deg)) else it })

    fun flipItem(s: EditState, id: Int): EditState =
        s.copy(scenery = s.scenery.map { if (it.id == id) it.copy(flip = !it.flip) else it })

    /**
     * Was liegt an der Stelle ([x], [y] relativ zum Bild)? Punkte haben den Fangradius ([rx], [ry]); Gegenstände und die Tür
     * werden über ihr Rechteck getroffen ([itemBox] liefert Breite und Höhe relativ zum Bild, [doorBox] ebenso). Kleine Dinge
     * (Aktionspunkte, Wegpunkte, Start) gehen vor Gegenständen, damit man sie auch vor einem großen Baum noch anfassen kann.
     */
    fun hit(
        s: EditState,
        x: Float,
        y: Float,
        rx: Float,
        ry: Float,
        itemBox: (SceneryItem) -> Pair<Float, Float>,
        doorBox: Pair<Float, Float>?,
        /** nur diese Arten werden getroffen (das aktive Werkzeug fasst nur seine eigenen Dinge an) */
        kinds: Set<SelKind> = SelKind.values().toSet(),
        /** Breite / Höhe des Bildes (für gedrehte Gegenstände und Türen) */
        aspect: Float = 1f,
    ): Sel? {
        fun near(p: Pt): Boolean {
            val dx = (p.x - x) / rx
            val dy = (p.y - y) / ry
            return dx * dx + dy * dy <= 1f
        }
        if (SelKind.STEP in kinds) for (i in s.script.indices.reversed()) if (s.script[i].at?.let { near(it) } == true) return Sel.Step(i)
        if (SelKind.PATH in kinds) for (i in s.path.indices.reversed()) if (near(s.path[i])) return Sel.Path(i)
        if (SelKind.START in kinds) s.start?.let { if (near(it)) return Sel.Start }
        if (SelKind.START in kinds) s.start2?.let { if (near(it)) return Sel.Start2 }
        if (SelKind.ITEM in kinds) for (item in s.scenery.reversed()) {
            val (w, h) = itemBox(item)
            val (lx, ly) = toLocal(x, y, item.at, item.rot, aspect)
            if (lx >= -w / 2 && lx <= w / 2 && ly >= -h && ly <= 0.02f) return Sel.Item(item.id)
        }
        val d = s.door
        if (SelKind.DOOR in kinds && d != null && doorBox != null) {
            val (w, h) = doorBox
            val (lx, ly) = toLocal(x, y, d, s.doorRot, aspect)
            if (lx >= -w / 2 && lx <= w / 2 && ly >= -h && ly <= 0.02f) return Sel.Door
        }
        return null
    }

    fun label(s: EditState, sel: Sel): String = when (sel) {
        Sel.Start -> "Startpunkt"
        Sel.Start2 -> "Startpunkt der Elfin"
        is Sel.Path -> "Wegpunkt ${sel.index + 1}"
        is Sel.Step -> "Aktion ${sel.index + 1}: " + (when (s.script.getOrNull(sel.index)?.who) { 1 -> "🧝‍♀️ "; 2 -> "🧙🧝‍♀️ "; else -> "" }) + (s.script.getOrNull(sel.index)?.action?.label ?: "")
        Sel.Door -> "Tür"
        is Sel.Item -> s.scenery.firstOrNull { it.id == sel.id }?.type?.label ?: "Gegenstand"
    }
}

/** Rückgängig / Wiederholen für den Editor. */
class EditHistory(private val limit: Int = 80) {
    private val undoStack = ArrayList<EditState>()
    private val redoStack = ArrayList<EditState>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Merkt [old] als Stand, zu dem „Rückgängig“ zurückkehrt; löscht die Wiederholen-Liste. */
    fun push(old: EditState) {
        undoStack.add(old)
        if (undoStack.size > limit) undoStack.removeAt(0)
        redoStack.clear()
    }

    /** Nimmt den zuletzt gemerkten Stand wieder weg (z. B. wenn ein Ziehen nichts verändert hat). */
    fun discardLast() {
        if (undoStack.isNotEmpty()) undoStack.removeAt(undoStack.lastIndex)
    }

    fun undo(current: EditState): EditState? {
        if (undoStack.isEmpty()) return null
        redoStack.add(current)
        return undoStack.removeAt(undoStack.lastIndex)
    }

    fun redo(current: EditState): EditState? {
        if (redoStack.isEmpty()) return null
        undoStack.add(current)
        return redoStack.removeAt(redoStack.lastIndex)
    }
}
