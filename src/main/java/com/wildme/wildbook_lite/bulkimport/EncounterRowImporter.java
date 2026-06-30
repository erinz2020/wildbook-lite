
package com.wildme.wildbook_lite.bulkimport;
import java.util.Map;
import org.springframework.stereotype.Component;


@Component
public class EncounterRowImporter extends AbstractRowImporter {

    @Override
    public String supportedType() {
        return "encounter";
    }

    @Override
    protected void persist(Map<String, String> fields) {
        String species = fields.get("species");
        String location = fields.get("location");

        if(species == null || species.isBlank()) {
            throw new IllegalArgumentException("species is required");
        }
    }
}