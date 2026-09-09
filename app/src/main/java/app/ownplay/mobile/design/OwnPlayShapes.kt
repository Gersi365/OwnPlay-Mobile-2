package app.ownplay.mobile.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object OwnPlayShapeTokens {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val Action = RoundedCornerShape(10.dp)
}

val OwnPlayMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = OwnPlayShapeTokens.Small,
    medium = OwnPlayShapeTokens.Medium,
    large = OwnPlayShapeTokens.Large,
    extraLarge = RoundedCornerShape(20.dp),
)
