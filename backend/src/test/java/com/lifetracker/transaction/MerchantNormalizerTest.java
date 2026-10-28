package com.lifetracker.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantNormalizerTest {

    @Test
    @DisplayName("Test 1: normalizeMerchant with SWIGGY*ORDER #123 gives 'swiggy order 123'; null/blank gives null")
    void testNormalizeMerchant() {
        assertThat(MerchantNormalizer.normalizeMerchant("SWIGGY*ORDER #123")).isEqualTo("swiggy order 123");
        assertThat(MerchantNormalizer.normalizeMerchant(null)).isNull();
        assertThat(MerchantNormalizer.normalizeMerchant("")).isNull();
        assertThat(MerchantNormalizer.normalizeMerchant("   ")).isNull();
        assertThat(MerchantNormalizer.normalizeMerchant("!!!###$$$")).isNull();
        assertThat(MerchantNormalizer.normalizeMerchant("  UBER   TRIP   #99  ")).isEqualTo("uber trip 99");
    }
}
