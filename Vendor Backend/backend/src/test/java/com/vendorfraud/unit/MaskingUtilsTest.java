package com.vendorfraud.unit;

import com.vendorfraud.common.MaskingUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingUtilsTest {

    @Test
    @DisplayName("only the last four characters survive masking")
    void masksAllButLastFour() {
        assertThat(MaskingUtils.maskAccountReference("SYN-AC-100001"))
                .endsWith("0001")
                .doesNotContain("SYN-AC")
                .hasSize("SYN-AC-100001".length());
    }

    @Test
    @DisplayName("a short reference is masked completely rather than partly exposed")
    void masksShortReferencesEntirely() {
        assertThat(MaskingUtils.maskAccountReference("1234")).isEqualTo("••••");
        assertThat(MaskingUtils.maskAccountReference("12")).isEqualTo("••");
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(MaskingUtils.maskAccountReference(null)).isNull();
        assertThat(MaskingUtils.maskAccountReference("   ")).isNull();
    }
}
