package com.platescanner.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.platescanner.app.ui.theme.PlateScannerTheme

/**
 * Placeholder for the permission rationale screen. The real version lives in
 * track 2; we keep this here so the navigation graph compiles without
 * additional dependencies.
 */
@Composable
fun PermissionRationaleScreen(
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "需要相机权限才能扫描",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("授予相机权限")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionRationaleScreenPreview() {
    PlateScannerTheme {
        Surface {
            PermissionRationaleScreen(onRequestPermission = {})
        }
    }
}
