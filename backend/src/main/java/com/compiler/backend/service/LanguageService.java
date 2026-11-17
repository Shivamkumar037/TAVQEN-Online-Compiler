package com.compiler.backend.service;

import com.compiler.backend.dto.LanguageDTO;
import com.compiler.backend.utility.Languages;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LanguageService {

    public LanguageDTO getLanguages() {
        List<Languages> languages = new ArrayList<>();



        languages.add(Languages.builder().name("JAVA").id(1).extension("java").version("15.0.2").build());

        languages.add(Languages.builder().name("PYTHON").id(2).extension("py").version("3.12.0").build());
        languages.add(Languages.builder().name("HTML").id(2).extension("html").version("HTML5").build());



        LanguageDTO languageDTO = new LanguageDTO();
        languageDTO.setLanguages(languages);
        return languageDTO;

    }

}
