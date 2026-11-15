package app.wichtelein.anim

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.max

/**
 * Die Wichtelfrau (Elfin): eine eigenständige, gezeichnete Figur mit Zipfelmütze, Zöpfen, spitzen Ohren und Kleid.
 * Sie benutzt dieselbe Haltung ([Pose]) wie der Wichtel; Szenen, Editor und Export merken keinen Unterschied.
 *
 * Gezeichnet wird in einem eigenen Maßstab: Füße bei (0, 0), Spitze der Mütze bei y = -[HEIGHT].
 */
class ElfinFigure(colors: ElfColors = ElfColors()) : ElfFigure {
    private val c = colors.clamped()

    // Originalfarben der Elfin: rote Mütze, grünes Kleid, rot-weiß geringelte Strumpfhose, braune Stiefel
    private val hat = if (c.hat == 0) Color(0xFFD93A3A) else ElfPalette.clothes[c.hat].swatch
    private val dress = if (c.jacket == 0) Color(0xFF2E9E4F) else ElfPalette.clothes[c.jacket].swatch
    private val tights: Color? = if (c.legs == 0) null else ElfPalette.clothes[c.legs].swatch
    private val boots = ElfPalette.boots[c.boots].swatch

    override fun draw(scope: DrawScope, pose: Pose, base: Offset, h: Float, facing: Int) {
        if (pose.scale < 0.02f) return
        val k = h / HEIGHT
        scope.withTransform({
            translate(base.x, base.y)
            scale(k * pose.scale * facing, k * pose.scale, Offset.Zero)
        }) {
            drawOval(Color.Black.copy(alpha = 0.25f), Offset(-210f, -17f), Size(420f, 34f))
            withTransform({
                translate(0f, -pose.bob * HEIGHT)
                scale(1f + pose.squash, 1f - pose.squash, Offset.Zero)
                rotate(pose.spin + pose.lean, Offset(0f, -430f))
            }) {
                drawLeg(-45f, pose.legL * 0.5f)
                drawLeg(45f, -pose.legR * 0.5f)
                drawBackHair(pose)
                drawArm(Offset(-82f, -615f), pose.armL, Prop.NONE)
                drawDress()
                // rechter Arm mit Gegenstand in der Hand
                drawArm(Offset(82f, -615f), -pose.armR, pose.prop, mirror = true)
                withTransform({
                    rotate(pose.hatTilt * 150f + pose.nod, Offset(0f, -690f))
                    scale(1f, 1f - 0.018f * pose.talk, Offset(0f, -690f))
                }) { drawHead(pose) }
            }
        }
    }

    private fun DrawScope.drawLeg(x: Float, angle: Float) {
        withTransform({ rotate(angle, Offset(x, -300f)) }) {
            val top = Offset(x, -300f)
            val foot = Offset(x, -70f)
            if (tights == null) {
                // rot-weiße Ringel
                drawLine(Color.White, top, foot, strokeWidth = 40f, cap = StrokeCap.Butt)
                var y = -300f
                while (y < -75f) {
                    drawLine(Color(0xFFD93A3A), Offset(x, y), Offset(x, minOf(y + 22f, -70f)), strokeWidth = 40f, cap = StrokeCap.Butt)
                    y += 44f
                }
            } else {
                drawLine(tights, top, foot, strokeWidth = 40f, cap = StrokeCap.Butt)
            }
            // Stiefel mit Zipfel
            drawRoundRect(boots, Offset(x - 32f, -80f), Size(64f, 62f), CornerRadius(14f))
            drawRoundRect(boots, Offset(x - 40f + (if (x < 0) -8f else 8f), -36f), Size(80f, 34f), CornerRadius(16f))
            drawCircle(Color(0xFFF2C94C), 7f, Offset(x, -66f))
        }
    }

    private fun DrawScope.drawDress() {
        val p = Path().apply {
            moveTo(-78f, -640f)
            lineTo(78f, -640f)
            lineTo(172f, -335f)
            lineTo(-172f, -335f)
            close()
        }
        drawPath(p, dress)
        // helle Schürze in der Mitte, Gürtel, Saum
        val apron = Path().apply {
            moveTo(-52f, -520f)
            lineTo(52f, -520f)
            lineTo(100f, -340f)
            lineTo(-100f, -340f)
            close()
        }
        drawPath(apron, Color(0xFFFFF3E0))
        drawRect(Color(0xFFD93A3A), Offset(-90f, -535f), Size(180f, 30f))
        drawCircle(Color(0xFFF2C94C), 14f, Offset(0f, -520f))
        drawRoundRect(Color.White, Offset(-176f, -352f), Size(352f, 28f), CornerRadius(14f))
        // kleine Tannenbäumchen auf der Schürze
        for (i in -1..1) {
            val cx = i * 38f
            val tree = Path().apply {
                moveTo(cx, -470f)
                lineTo(cx + 18f, -420f)
                lineTo(cx - 18f, -420f)
                close()
            }
            drawPath(tree, dress)
        }
        // Kragen
        drawCircle(Color.White, 34f, Offset(0f, -640f))
    }

    /** Arm mit Ärmel und Hand um die Schulter [pivot]; [angle] > 0 = im Uhrzeigersinn (nach außen für den linken Arm). */
    private fun DrawScope.drawArm(pivot: Offset, angle: Float, prop: Prop, mirror: Boolean = false) {
        withTransform({ rotate(angle, pivot) }) {
            val hand = Offset(pivot.x + (if (mirror) 6f else -6f), pivot.y + 225f)
            if (prop != Prop.NONE) drawHandProp(prop, hand)
            drawLine(dress, pivot, Offset(hand.x, hand.y - 60f), strokeWidth = 44f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(hand.x, hand.y - 70f), Offset(hand.x, hand.y - 48f), strokeWidth = 50f, cap = StrokeCap.Round)
            drawCircle(SKIN, 27f, hand)
        }
    }

    private fun DrawScope.drawHandProp(prop: Prop, hand: Offset) {
        when (prop) {
            Prop.BROOM -> {
                // Besen: Stiel durch die Hand, Strohborsten bis zum Boden
                val top = Offset(hand.x, hand.y - 250f)
                val bottom = Offset(hand.x, hand.y + 290f)
                drawLine(Color(0xFF8A5A2B), top, bottom, strokeWidth = 13f, cap = StrokeCap.Round)
                val bristles = Path().apply {
                    moveTo(hand.x - 14f, hand.y + 250f)
                    lineTo(hand.x + 14f, hand.y + 250f)
                    lineTo(hand.x + 62f, hand.y + 400f)
                    lineTo(hand.x - 62f, hand.y + 400f)
                    close()
                }
                drawPath(bristles, Color(0xFFD9B25B))
                drawLine(Color(0xFFB88A3A), Offset(hand.x - 8f, hand.y + 262f), Offset(hand.x - 38f, hand.y + 398f), strokeWidth = 5f)
                drawLine(Color(0xFFB88A3A), Offset(hand.x + 8f, hand.y + 262f), Offset(hand.x + 38f, hand.y + 398f), strokeWidth = 5f)
                drawRect(Color(0xFFD93A3A), Offset(hand.x - 17f, hand.y + 240f), Size(34f, 20f))
            }
            Prop.COOKIE -> {
                drawCircle(Color(0xFFD9A05B), 42f, Offset(hand.x, hand.y - 30f))
                drawCircle(Color(0xFF5B3A24), 7f, Offset(hand.x - 12f, hand.y - 42f))
                drawCircle(Color(0xFF5B3A24), 7f, Offset(hand.x + 14f, hand.y - 18f))
            }
            Prop.GIFT -> {
                drawRoundRect(Color(0xFFD93A3A), Offset(hand.x - 50f, hand.y - 90f), Size(100f, 85f), CornerRadius(8f))
                drawRect(Color(0xFFF2C94C), Offset(hand.x - 8f, hand.y - 90f), Size(16f, 85f))
                drawRect(Color(0xFFF2C94C), Offset(hand.x - 50f, hand.y - 57f), Size(100f, 14f))
            }
            Prop.NONE -> Unit
        }
    }

    /** Zöpfe hinter dem Kopf (hängen über die Schultern). */
    private fun DrawScope.drawBackHair(p: Pose) {
        for (s in intArrayOf(-1, 1)) {
            for (i in 0..5) {
                drawCircle(HAIR, 25f - i * 1.5f, Offset(s * (118f - i * 2f), -735f + i * 42f))
            }
            drawCircle(Color(0xFFD93A3A), 15f, Offset(s * 106f, -490f))
        }
    }

    private fun DrawScope.drawHead(p: Pose) {
        val cx = 0f
        val cy = -765f
        // Ohren (spitz)
        for (s in intArrayOf(-1, 1)) {
            val ear = Path().apply {
                moveTo(s * 98f, cy - 15f)
                lineTo(s * 168f, cy - 55f)
                lineTo(s * 100f, cy + 30f)
                close()
            }
            drawPath(ear, SKIN)
            val inner = Path().apply {
                moveTo(s * 108f, cy - 5f)
                lineTo(s * 152f, cy - 42f)
                lineTo(s * 108f, cy + 18f)
                close()
            }
            drawPath(inner, Color(0xFFF2A99B))
        }
        // Gesicht
        drawOval(SKIN, Offset(cx - 104f, cy - 108f), Size(208f, 218f))
        // Pony unter der Mütze
        val fringe = Path().apply {
            moveTo(cx - 106f, cy - 40f)
            lineTo(cx - 60f, cy - 70f)
            lineTo(cx - 20f, cy - 36f)
            lineTo(cx + 24f, cy - 72f)
            lineTo(cx + 64f, cy - 38f)
            lineTo(cx + 106f, cy - 62f)
            lineTo(cx + 106f, cy - 100f)
            lineTo(cx - 106f, cy - 100f)
            close()
        }
        drawPath(fringe, HAIR)
        // Augen
        val closed = p.eyesClosed || p.blink
        for (s in intArrayOf(-1, 1)) {
            val ex = cx + s * 40f
            val ey = cy + 8f
            if (closed) {
                drawArc(INK, 10f, 160f, false, Offset(ex - 18f, ey - 14f), Size(36f, 24f), style = Stroke(width = 5f, cap = StrokeCap.Round))
            } else {
                drawOval(Color.White, Offset(ex - 17f, ey - 20f), Size(34f, 40f))
                drawCircle(Color(0xFF3A7BD9), 13f, Offset(ex, ey))
                drawCircle(INK, 7f, Offset(ex, ey))
                drawCircle(Color.White, 4f, Offset(ex + 4f, ey - 5f))
                drawLine(INK, Offset(ex - 18f, ey - 18f), Offset(ex - 24f, ey - 25f), strokeWidth = 4f, cap = StrokeCap.Round)
            }
        }
        // Wangen, Nase
        drawCircle(Color(0xFFF08C8C).copy(alpha = 0.55f), 20f, Offset(cx - 68f, cy + 50f))
        drawCircle(Color(0xFFF08C8C).copy(alpha = 0.55f), 20f, Offset(cx + 68f, cy + 50f))
        drawCircle(Color(0xFFE59A86), 8f, Offset(cx, cy + 40f))
        // Mund: Lächeln oder offen beim Sprechen
        val open = max(p.mouth, p.talk)
        if (open > 0.05f) {
            val mh = 10f + 34f * open
            drawOval(Color(0xFF7A2323), Offset(cx - 22f, cy + 66f), Size(44f, mh))
            if (open > 0.5f) drawOval(Color(0xFFD9667A), Offset(cx - 13f, cy + 66f + mh - 14f), Size(26f, 11f))
        } else {
            drawArc(INK, 20f, 140f, false, Offset(cx - 22f, cy + 52f), Size(44f, 26f), style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
        // Mütze: Zipfel mit weißem Rand und Bommel
        val cap = Path().apply {
            moveTo(cx - 112f, cy - 70f)
            lineTo(cx - 70f, cy - 175f)
            lineTo(cx - 20f, cy - 235f)
            lineTo(cx + 52f, cy - 262f)
            lineTo(cx + 96f, cy - 225f)
            lineTo(cx + 78f, cy - 190f)
            lineTo(cx + 112f, cy - 70f)
            close()
        }
        drawPath(cap, hat)
        drawRoundRect(Color.White, Offset(cx - 124f, cy - 104f), Size(248f, 44f), CornerRadius(22f))
        drawCircle(Color.White, 30f, Offset(cx + 92f, cy - 220f))
        // Mützenstern
        drawCircle(Color(0xFFF2C94C), 10f, Offset(cx - 28f, cy - 160f))
    }

    companion object {
        /** Höhe der Figur im eigenen Maßstab (von den Füßen bis zur Mützenspitze) */
        const val HEIGHT = 1030f
        private val SKIN = Color(0xFFFBD5B5)
        private val HAIR = Color(0xFF8A4B2A)
        private val INK = Color(0xFF3A2A20)
    }
}
