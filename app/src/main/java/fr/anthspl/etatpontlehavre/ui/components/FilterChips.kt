package fr.anthspl.etatpontlehavre.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.anthspl.etatpontlehavre.ui.theme.EtatPontLeHavreTheme
import fr.anthspl.etatpontlehavre.ui.viewmodel.FilterType

/**
 * Composant réutilisable affichant une rangée horizontale de puces de filtrage ([FilterChip]).
 *
 * Permet à l'utilisateur de basculer rapidement entre les catégories : Tous, Favoris, Ouverts, Fermés.
 *
 * @param selectedFilter Filtre actuellement sélectionné.
 * @param onFilterSelected Callback invoqué lorsque l'utilisateur clique sur un nouveau filtre.
 * @param modifier Modificateur de mise en page Compose.
 */
@Composable
fun FilterChipsRow(
    selectedFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(FilterType.entries.toTypedArray()) { filter ->
            val isSelected = filter == selectedFilter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = { Text(text = filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

/**
 * Aperçu Compose (Preview) pour la barre de filtres.
 */
@Preview(showBackground = true, name = "Puces de filtrage")
@Composable
fun FilterChipsRowPreview() {
    EtatPontLeHavreTheme {
        FilterChipsRow(
            selectedFilter = FilterType.ALL,
            onFilterSelected = {}
        )
    }
}
