package it.tuonome.ortofrutta.lookup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LookupSlugTest {

    @Test
    void buildsStableCodesFromNames() {
        assertThat(LookupService.slug("Frutta secca")).isEqualTo("FRUTTA_SECCA");
        assertThat(LookupService.slug("  Carta / Bancomat ")).isEqualTo("CARTA_BANCOMAT");
    }

    @Test
    void removesAccents() {
        assertThat(LookupService.slug("Perché è così")).isEqualTo("PERCHE_E_COSI");
    }

    @Test
    void neverReturnsAnEmptyCode() {
        assertThat(LookupService.slug("  --  ")).isEqualTo("VALORE");
    }
}
