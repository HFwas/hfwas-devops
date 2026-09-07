package com.hfwas.devops.image.service.transform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageIoTransformerMemoryTest {

    @Test
    void subsampleIsOneWhenImageAlreadyWithinMaxSide() {
        assertEquals(1, ImageIoTransformer.subsampleFactor(100, 50, 200));
        assertEquals(1, ImageIoTransformer.subsampleFactor(32, 32, 32));
    }

    @Test
    void subsampleReducesLargeRastersBeforeDecode() {
        assertEquals(2, ImageIoTransformer.subsampleFactor(100, 50, 50));
        assertEquals(3, ImageIoTransformer.subsampleFactor(8000, 4000, 2048));
    }
}
