package com.hfwas.devops.image.service.metadata;

import com.hfwas.devops.image.TestImages;
import com.hfwas.devops.image.dto.ImageMetadataVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void jpegWithoutExifHasNoGps() throws Exception {
        Path file = TestImages.writeJpeg(tempDir, "plain.jpg", 24, 16);
        MetadataExtractorReader reader = new MetadataExtractorReader();
        ImageMetadataVO vo = reader.read(file, "image/jpeg", "jpg", 24, 16, 1, false);
        assertEquals(24, vo.getPixel().getWidth());
        assertEquals(16, vo.getPixel().getHeight());
        assertEquals("image/jpeg", vo.getFormat().getMime());
        assertFalse(vo.getPrivacy().isHasGps());
    }

    @Test
    void exiftoolParserMapsGpsAndCapture() {
        ExifToolReader reader = new ExifToolReader(null, null, null);
        String out = """
                Make                            : Apple
                Model                           : iPhone 15
                DateTimeOriginal                : 2026:09:01 12:00:00
                Orientation                     : 6
                GPSLatitude                     : 31.23
                GPSLongitude                    : 121.47
                ColorSpace                      : sRGB
                """;
        ImageMetadataVO vo = reader.parse(out, "image/jpeg", "jpg", 4032, 3024, 1, false);
        assertEquals("Apple", vo.getCapture().getMake());
        assertEquals("iPhone 15", vo.getCapture().getModel());
        assertEquals(6, vo.getCapture().getOrientation());
        assertEquals("2026-09-01T12:00:00", vo.getCapture().getTakenAt());
        assertNotNull(vo.getGps());
        assertEquals(31.23, vo.getGps().getLat());
        assertEquals(121.47, vo.getGps().getLng());
        assertTrue(vo.getPrivacy().isHasGps());
        assertEquals(4032, vo.getPixel().getWidth());
    }

    @Test
    void exiftoolParserWithoutGps() {
        ExifToolReader reader = new ExifToolReader(null, null, null);
        ImageMetadataVO vo = reader.parse("Make : Canon\n", "image/jpeg", "jpg", 10, 10, 1, false);
        assertFalse(vo.getPrivacy().isHasGps());
        assertEquals("Canon", vo.getCapture().getMake());
    }

    @Test
    void exiftoolJsonFillsRawTagsMakerNotesAndFaceFlag() {
        ExifToolReader reader = new ExifToolReader(null, null, null);
        String json = """
                [{
                  "SourceFile": "a.jpg",
                  "EXIF:Make": "Apple",
                  "EXIF:Model": "iPhone 15",
                  "EXIF:Orientation": 6,
                  "EXIF:DateTimeOriginal": "2026:09:01 12:00:00",
                  "EXIF:GPSLatitude": 31.23,
                  "EXIF:GPSLongitude": 121.47,
                  "EXIF:ColorSpace": "sRGB",
                  "EXIF:Copyright": "All rights reserved",
                  "EXIF:Artist": "Ada",
                  "MakerNotes:LensModel": "iPhone 15 back camera",
                  "XMP:RegionInfo": {"RegionList": [{"Name": "face"}]}
                }]
                """;
        ImageMetadataVO vo = reader.parseJson(json, "image/jpeg", "jpg", 4032, 3024, 1, false);
        assertEquals("Apple", vo.getCapture().getMake());
        assertEquals("iPhone 15", vo.getCapture().getModel());
        assertEquals(6, vo.getCapture().getOrientation());
        assertEquals("Ada", vo.getCopyright().getArtist());
        assertEquals("All rights reserved", vo.getCopyright().getCopyright());
        assertTrue(vo.getPrivacy().isHasGps());
        assertTrue(vo.getPrivacy().isHasFaceRegions());
        assertEquals("iPhone 15 back camera", vo.getRawTags().get("MakerNotes:LensModel"));
        assertFalse(vo.getRawTags().containsKey("SourceFile"));
    }
}
