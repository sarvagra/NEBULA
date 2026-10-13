package com.nebula.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebula.app.domain.model.AttachmentType
import com.nebula.app.domain.model.ChatAttachment
import com.nebula.app.theme.Theme
import com.nebula.app.ui.components.PABottomSheet
import java.util.UUID

/**
 * AttachmentPickerSheet:
 * Action surface for attaching Document, Image, Camera, or File to the chat composer.
 */
@Composable
fun AttachmentPickerSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onAttachmentSelected: (ChatAttachment) -> Unit
) {
    val theme = Theme.current

    PABottomSheet(
        isVisible = isVisible,
        onDismissRequest = onDismiss,
        title = "Attach Context"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = theme.spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AttachmentOptionRow(
                icon = Icons.Default.Description,
                title = "Document",
                subtitle = "PDF, TXT, Markdown, or Notes",
                onClick = {
                    onAttachmentSelected(
                        ChatAttachment(
                            id = "att_${UUID.randomUUID().toString().take(6)}",
                            name = "Project_Spec.pdf",
                            mimeType = "application/pdf",
                            sizeLabel = "384 KB",
                            type = AttachmentType.DOCUMENT
                        )
                    )
                    onDismiss()
                }
            )

            AttachmentOptionRow(
                icon = Icons.Default.Image,
                title = "Photo Library",
                subtitle = "Select photos or screenshots",
                onClick = {
                    onAttachmentSelected(
                        ChatAttachment(
                            id = "att_${UUID.randomUUID().toString().take(6)}",
                            name = "Screen_Capture.png",
                            mimeType = "image/png",
                            sizeLabel = "1.2 MB",
                            type = AttachmentType.IMAGE
                        )
                    )
                    onDismiss()
                }
            )

            AttachmentOptionRow(
                icon = Icons.Default.CameraAlt,
                title = "Camera",
                subtitle = "Capture photo with camera",
                onClick = {
                    onAttachmentSelected(
                        ChatAttachment(
                            id = "att_${UUID.randomUUID().toString().take(6)}",
                            name = "Camera_Photo.jpg",
                            mimeType = "image/jpeg",
                            sizeLabel = "2.4 MB",
                            type = AttachmentType.CAMERA
                        )
                    )
                    onDismiss()
                }
            )

            AttachmentOptionRow(
                icon = Icons.Default.Folder,
                title = "Local Files",
                subtitle = "Browse device storage securely",
                onClick = {
                    onAttachmentSelected(
                        ChatAttachment(
                            id = "att_${UUID.randomUUID().toString().take(6)}",
                            name = "Dataset.csv",
                            mimeType = "text/csv",
                            sizeLabel = "96 KB",
                            type = AttachmentType.FILE
                        )
                    )
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val theme = Theme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.radii.lg))
            .background(theme.colors.surface)
            .border(1.dp, theme.colors.surfaceBorder, RoundedCornerShape(theme.radii.lg))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(theme.radii.sm))
                .background(theme.colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = theme.colors.primaryAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = title,
                style = theme.typography.titleMedium.copy(
                    color = theme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            )
            Text(
                text = subtitle,
                style = theme.typography.caption.copy(
                    color = theme.colors.textSecondary
                )
            )
        }
    }
}
