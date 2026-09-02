package com.hfwas.devops;

import com.hfwas.devops.fileparser.ocr.OcrNativeMemory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DevopsApplication {
    public static void main(String[] args) {
        OcrNativeMemory.configure();
        SpringApplication.run(DevopsApplication.class, args);
    }
}
