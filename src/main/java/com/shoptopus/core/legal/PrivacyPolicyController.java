package com.shoptopus.core.legal;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrivacyPolicyController {

    @GetMapping(value = {"/privacy-policy", "/privacy-policy/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> privacyPolicy() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .body(new ClassPathResource("legal/privacy-policy.html"));
    }
}
